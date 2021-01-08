#include "Uniforms.glsl"
#include "Samplers.glsl"
#include "Transform.glsl"
#include "ScreenPos.glsl"

varying highp vec2 vScreenPos;

#ifdef COMPILEVS

void VS()
{
    mat4 modelMatrix = iModelMatrix;
    vec3 worldPos = GetWorldPos(modelMatrix);
    gl_Position = GetClipPos(worldPos);
    vScreenPos = GetScreenPosPreDiv(gl_Position);
}

#endif


#ifdef COMPILEPS
uniform sampler2D sRnd0; // Random noise
uniform sampler2D sDep1; // Depth Buffer
uniform vec2 cNoiseScale;

float camClip = cFarClipPS - cNearClipPS;

const vec2 kernel[4] = vec2[](
    vec2( 1, 0),
    vec2( 0, 1),
    vec2(-1, 0),
    vec2( 0,-1)
);

float GetDepth(sampler2D depthSampler, vec2 uv)
{
    #ifdef HWDEPTH
        return ReconstructDepth(texture2D(depthSampler, uv).r);
    #else
        return DecodeDepth(texture2D(depthSampler, uv).rgb);
    #endif
}

// Port from: https://github.com/jsj2008/Zombie-Blobs/blob/278e16229ccb77b2e11d788082b2ccebb9722ace/src/postproc.fs

// see T M?ller, 1999: Efficiently building a matrix to rotate one vector to another
mat3 rotateNormalVecToAnother(vec3 f, vec3 t) {
    vec3 v = cross(f, t);
    float c = dot(f, t);
    float h = (1.0 - c) / (1.0 - c * c);
    return mat3(c + h * v.x * v.x, h * v.x * v.y + v.z, h * v.x * v.z - v.y,
                h * v.x * v.y - v.z, c + h * v.y * v.y, h * v.y * v.z + v.x,
                h * v.x * v.z + v.y, h * v.y * v.z - v.x, c + h * v.z * v.z);
}

vec3 normal_from_depth(float depth, highp vec2 texcoords) {
    // One pixel: 0.001 = 1 / 1000 (pixels)
    vec2 offset1 = vec2(0.0, cGBufferInvSize.y);
    vec2 offset2 = vec2(cGBufferInvSize.x, 0.0);

    float depth1 = GetDepth(sDep1, texcoords + offset1);
    float depth2 = GetDepth(sDep1, texcoords + offset2);
    
    vec3 p1 = vec3(offset1, depth1 - depth);
    vec3 p2 = vec3(offset2, depth2 - depth);
    
    highp vec3 normal = cross(p1, p2);
    normal.z = -normal.z;
    
    return normalize(normal);
}

void PS()
{
    const float aoStrength = 1.0;
    const float radius = 0.3;
    
    highp vec2 tx = vScreenPos;
    highp vec2 px = cGBufferInvSize;
    
    float depth = GetDepth(sDep1, vScreenPos);
    vec3  normal = normal_from_depth(depth, vScreenPos);
    vec2 random = texture2D(sRnd0, vScreenPos / (cNoiseScale * cGBufferInvSize)).xy * 2.0 - 1.0;
    
    // radius is in world space unit
    float rad = radius / depth;
    float zRange = radius / camClip;
    
    // calculate inverse matrix of the normal by rotate it to identity
    mat3 InverseNormalMatrix = rotateNormalVecToAnother(normal, vec3(0.0, 0.0, 1.0));
    
    // result of line sampling
    // See Loos & Sloan: Volumetric Obscurance
    // http://www.cs.utah.edu/~loos/publications/vo/vo.pdf
    float hemi = 0.0;
    float maxi = 0.0;
    
    for (int i = 0; i < 4; ++i) {
        // make virtual sphere of unit volume, more closer to center, more ambient occlusion contributions
        vec2 ray = reflect(kernel[i], random.xy);
        float rx = 0.3 * ray.x;
        float ry = 0.3 * ray.y;
        float rz = sqrt(1.0 - rx * rx - ry * ry);
        
        highp vec3 screenCoord = vec3(ray.x * px.x, ray.y * px.y, 0.0);
        // 0.25 times smaller when farest, 5.0 times bigger when nearest.
        highp vec2 coord = tx + rad * screenCoord.xy;
        // fetch depth from texture
        screenCoord.z = GetDepth(sDep1, coord);
        // move to origin
        screenCoord.z -= depth;

        if (screenCoord.z * radius < -zRange) continue;

        // Transform to normal-oriented hemisphere space
        highp vec3 localCoord = InverseNormalMatrix * screenCoord;
        // ralative depth in the world space radius
        float dr = localCoord.z / zRange;
        // calculate contribution
        float v = clamp(rz + dr * aoStrength, 0.0, 2.0 * rz);

        maxi += rz;
        hemi += v;
    }

    float ao = clamp(hemi/maxi, 0.0, 1.0);

    gl_FragColor.rgb = vec3(ao);
    gl_FragColor.a = 1.0;
}

#endif