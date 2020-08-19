using import radlib.core-extensions
using import struct
using import glsl
using import glm
using import enum

enum DescriptorSet plain
    Transform
    Textures
    Attributes

enum Sprite2DAttribute plain
    Position
    TextureCoordinates
    Color
   
struct SpriteQuad plain
    position : vec2
    rotation : f32
    layer   : u32
    size     : vec2
    color    : vec4

define-scope sprite2d-vs
    uniform transform : (tuple mat4)
        set = DescriptorSet.Transform
        binding = 0
    let transform = `(extractvalue transform 0)

    buffer sprites :
        struct SpriteQuadArray plain
            arr : (array SpriteQuad)
        set = DescriptorSet.Attributes
        binding = 0
    let sprites = `(extractvalue sprites 0)

    out vtexcoord : vec3
        location = Sprite2DAttribute.TextureCoordinates
    out vcolor : vec4
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
    in vtexcoord : vec3
        location = Sprite2DAttribute.TextureCoordinates
    out fcolor : vec4
        location = 0 # must match color attachment!

locals;
