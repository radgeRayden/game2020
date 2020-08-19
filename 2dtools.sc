using import radlib.core-extensions
using import Array
using import struct
using import glm
using import property
import .wgpu
import .gfxstate
import .shader-bindings
import .gfx.descriptors

struct TextureQuad plain
    position : vec2
    extent : vec2

struct SpriteBatch
    Sprite          := shader-bindings.SpriteQuad
    SpriteLimit     := ((2 ** 16) // 6)
    MaxIndexCount   := (SpriteLimit * 6)
    IndexBufferSize := ((sizeof u16) * MaxIndexCount)
    QuadBufferSize  := ((sizeof Sprite) * SpriteLimit)

    sbuffer            : wgpu.BufferId
    ibuffer            : wgpu.BufferId
    texture-binding    : wgpu.BindGroupId
    attribute-binding  : wgpu.BindGroupId
    sprites            : (Array Sprite)


    vvv bind SpriteCount
    property
        inline "get" (self)
            (countof self.sprites)

    # I hate this interface. Probably have to implement bind group caching?
    # Either way I can't be passing bindings around like this. Layouts
    # definitely need to be cached.
    inline __typecall (cls texture-binding attribute-binding-layout)
        # index buffer never changes (while we don't have batch resizing),
        # so we can just generate it at the start and leave it be.
        local indices = ((Array u16))
        'resize indices MaxIndexCount
        # fill in index data
        for i in (range SpriteLimit)
            let vertex-offset = ((i * 4) as u16)
            let
                top-left     = (vertex-offset + 0:u16)
                top-right    = (vertex-offset + 1:u16)
                bottom-left  = (vertex-offset + 2:u16)
                bottom-right = (vertex-offset + 3:u16)

            let index-offset = (i * 6)
            indices @ index-offset       = top-left
            indices @ (index-offset + 1) = bottom-left
            indices @ (index-offset + 2) = bottom-right
            indices @ (index-offset + 3) = bottom-right
            indices @ (index-offset + 4) = top-right
            indices @ (index-offset + 5) = top-left

        let device queue =
            'force-unwrap gfxstate.istate.device
            gfxstate.istate.queue
        let ibuffer =
            wgpu.device_create_buffer device
                &local wgpu.BufferDescriptor
                    label = "spritebatch indices"
                    size = IndexBufferSize
                    usage =
                        wgpu.BufferUsage_COPY_DST | wgpu.BufferUsage_INDEX

        wgpu.queue_write_buffer queue
            \ ibuffer 0 ((imply indices pointer) as (pointer u8)) IndexBufferSize

        local sprites = ((Array Sprite))
        let sbuffer-size =
            (sizeof Sprite) * SpriteLimit
        let sbuffer =
            wgpu.device_create_buffer device
                &local wgpu.BufferDescriptor
                    label = "spritebatch vertex attributes"
                    size = sbuffer-size
                    usage =
                        wgpu.BufferUsage_COPY_DST | wgpu.BufferUsage_STORAGE

        let sbuffer-binding =
            wgpu.device_create_bind_group device
                &local wgpu.BindGroupDescriptor
                    label = "transform bind group"
                    layout = attribute-binding-layout
                    entries =
                        &local (gfx.descriptors.bindings.Buffer 0 sbuffer 0 sbuffer-size)
                    entries_length = 1

        super-type.__typecall cls
            ibuffer = ibuffer
            sbuffer = sbuffer
            sprites = ((Array Sprite))
            texture-binding = texture-binding
            attribute-binding = sbuffer-binding

    fn draw (self render-pass)
        wgpu.queue_write_buffer gfxstate.istate.queue self.sbuffer 0
            (imply self.sprites pointer) as (pointer u8)
            (sizeof Sprite) * (countof self.sprites)

        sets := shader-bindings.DescriptorSet
        # wgpu.render_pass_set_vertex_buffer render-pass 0 self.vbuffer 0 (sizeof VertexAttributes)
        wgpu.render_pass_set_bind_group render-pass
            \ sets.Textures self.texture-binding null 0
        wgpu.render_pass_set_bind_group render-pass
            \ sets.Attributes self.attribute-binding null 0
        wgpu.render_pass_set_index_buffer render-pass self.ibuffer 0 IndexBufferSize
        # 2 triangles per sprite
        let index-count = ((self.SpriteCount * 6:usize) as u32)
        wgpu.render_pass_draw_indexed render-pass index-count 1 0 0 0
        ;

    fn clear (self)
        'clear self.sprites
        ;
    fn... add (self, x : i32, y : i32, width : i32, height : i32, orientation : f32, sprite-layer : u32)
        using import math

        let id = (countof self.sprites)
        'append self.sprites
            Sprite
                position = (vec2 x y)
                rotation = orientation
                layer = sprite-layer
                size = (vec2 width height)
                color = (vec4 1)
        id
locals;
