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
uniform bool useShadows;
uniform bool unlit;          // true = benda emisif (penanda posisi cahaya)

// Mengembalikan 0.0 (terang) .. 1.0 (bayangan penuh), dirata-rata dengan PCF 3x3.
float calcShadow(vec4 posLightSpace, vec3 N, vec3 L) {
    vec3 proj = posLightSpace.xyz / posLightSpace.w;
    proj = proj * 0.5 + 0.5;                       // NDC [-1,1] -> [0,1]
    if (proj.z > 1.0 || proj.x < 0.0 || proj.x > 1.0 || proj.y < 0.0 || proj.y > 1.0)
        return 0.0;                                // di luar frustum cahaya

    // Slope-scaled bias: lebih besar saat permukaan miring terhadap cahaya.
    // Skala disesuaikan dengan rentang depth ortho (near 1 .. far 25) agar tidak terjadi Peter Panning.
    float bias = max(0.0035 * (1.0 - dot(N, L)), 0.0008);

    vec2 texel = 1.0 / vec2(textureSize(shadowMap, 0));
    float shadow = 0.0;
    for (int x = -1; x <= 1; ++x) {
        for (int y = -1; y <= 1; ++y) {
            float dShadow = texture(shadowMap, proj.xy + vec2(x, y) * texel).r;
            shadow += (proj.z - bias > dShadow) ? 1.0 : 0.0;   // d_current > d_shadow + bias
        }
    }
    return shadow / 9.0;
}

void main() {
    if (unlit) {
        FragColor = vec4(objectColor, 1.0);
        return;
    }

    // 1. Ambient Lighting
    float ambientStrength = 0.2;
    vec3 ambient = ambientStrength * lightColor;

    // 2. Diffuse Lighting (Lambertian)
    vec3 norm = normalize(Normal);
    vec3 lightDir = normalize(lightPos - FragPos);
    float diff = max(dot(norm, lightDir), 0.0);
    vec3 diffuse = diff * lightColor;

    // 3. Specular Lighting (Phong vs Blinn-Phong)
    float specularStrength = 0.8;
    vec3 viewDir = normalize(viewPos - FragPos);
    float spec = 0.0;
    if (useBlinnPhong) {
        vec3 halfwayDir = normalize(lightDir + viewDir);
        spec = pow(max(dot(norm, halfwayDir), 0.0), 32.0);   // Blinn-Phong
    } else {
        vec3 reflectDir = reflect(-lightDir, norm);
        spec = pow(max(dot(viewDir, reflectDir), 0.0), 16.0); // Standard Phong
    }
    if (diff == 0.0) spec = 0.0;  // permukaan membelakangi cahaya: tidak ada kilau
    vec3 specular = specularStrength * spec * lightColor;

    // 4. Shadow: Shadow Factor 1.0 (terang) .. 0.2 (bayangan)
    float shadowFactor = 1.0;
    if (useShadows) {
        shadowFactor = 1.0 - 0.8 * calcShadow(FragPosLightSpace, norm, lightDir);
    }

    // Combine Texture & Lighting (ambient tidak ikut dibayangi)
    vec3 baseColor = useTexture ? texture(textureSampler, TexCoords).rgb * objectColor : objectColor;
    vec3 result = (ambient + shadowFactor * (diffuse + specular)) * baseColor;
    FragColor = vec4(result, 1.0);
}
