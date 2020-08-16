using import radlib.core-extensions
using import struct
using import glsl
using import glm
using import enum

enum DescriptorSet plain
    Transform
    Textures

enum Sprite2DAttribute plain
    Position
    TextureCoordinates
    Color
   
define-scope sprite2d-vs
    uniform transform : (tuple mat4)
        set = DescriptorSet.Transform
        binding = 0
    let transform = `(extractvalue transform 0)
    in vposition : vec2
        location = Sprite2DAttribute.Position
    inout vtexcoord : vec3
        location = Sprite2DAttribute.TextureCoordinates
    inout vcolor : vec4
        location = Sprite2DAttribute.Color

define-scope sprite2d-fs
    uniform diffuse-t : texture2D
        set = DescriptorSet.Textures
        binding = 0
    uniform diffuse-s : sampler
        set = DescriptorSet.Textures
        binding = 1
    in vcolor : vec4
        location = Sprite2DAttribute.Color
    out fcolor : vec4
        location = 0
    in vtexcoord : vec3
        location = Sprite2DAttribute.TextureCoordinates

locals;
