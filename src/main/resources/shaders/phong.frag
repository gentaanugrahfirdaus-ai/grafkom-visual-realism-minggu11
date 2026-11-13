#version 330 core
out vec4 FragColor;

in vec3 FragPos;
in vec3 Normal;
in vec2 TexCoords;
in vec4 FragPosLightSpace;

uniform vec3 lightPos;
uniform vec3 viewPos;
uniform vec3 lightColor;
uniform vec3 objectColor;
uniform sampler2D textureSampler;
uniform sampler2D shadowMap;
uniform bool useTexture;
uniform bool useBlinnPhong;
uniform bool useShadow;
uniform bool useBias;

// Shadow Mapping: Render Pass 2 (bandingkan depth fragment dgn Shadow Map)
float calcShadow(vec4 fragLight, vec3 norm, vec3 lightDir) {
    vec3 proj = fragLight.xyz / fragLight.w;   // perspective divide
    proj = proj * 0.5 + 0.5;                   // NDC [-1,1] -> [0,1]
    if (proj.z > 1.0) return 0.0;              // di luar far plane cahaya

    // Slope-scaled bias (disetel utk ortho depth range 1..25)
    float bias = useBias ? max(0.002 * (1.0 - dot(norm, lightDir)), 0.0005) : 0.0;

    // PCF 3x3 supaya tepi bayangan halus
    float shadow = 0.0;
    vec2 texel = 1.0 / vec2(textureSize(shadowMap, 0));
    for (int x = -1; x <= 1; ++x) {
        for (int y = -1; y <= 1; ++y) {
            float closest = texture(shadowMap, proj.xy + vec2(x, y) * texel).r;
            shadow += (proj.z - bias > closest) ? 1.0 : 0.0;
        }
    }
    return shadow / 9.0;
}

void main() {
    vec3 norm = normalize(Normal);
    if (!gl_FrontFacing) norm = -norm; // supaya sisi belakang tetap terang saat culling OFF

    // 1. Ambient
    float ambientStrength = 0.2;
    vec3 ambient = ambientStrength * lightColor;

    // 2. Diffuse (Lambertian)
    vec3 lightDir = normalize(lightPos - FragPos);
    float diff = max(dot(norm, lightDir), 0.0);
    vec3 diffuse = diff * lightColor;

    // 3. Specular (Phong vs Blinn-Phong)
    float specularStrength = 0.8;
    vec3 viewDir = normalize(viewPos - FragPos);
    float spec = 0.0;
    if (useBlinnPhong) {
        vec3 halfwayDir = normalize(lightDir + viewDir);
        spec = pow(max(dot(norm, halfwayDir), 0.0), 32.0);   // Blinn-Phong
    } else {
        vec3 reflectDir = reflect(-lightDir, norm);
        spec = pow(max(dot(viewDir, reflectDir), 0.0), 16.0); // Phong standar
    }
    vec3 specular = specularStrength * spec * lightColor;

    // 4. Shadow (faktor 0.2 saat di bayangan => 1 - 0.8)
    float shadow = useShadow ? calcShadow(FragPosLightSpace, norm, lightDir) : 0.0;
    float lit = 1.0 - shadow * 0.8;

    // 5. Texture + warna objek
    vec3 baseColor = useTexture ? texture(textureSampler, TexCoords).rgb * objectColor : objectColor;

    vec3 result = (ambient + lit * (diffuse + specular)) * baseColor;
    FragColor = vec4(result, 1.0);
}
