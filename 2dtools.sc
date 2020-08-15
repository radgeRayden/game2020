using import radlib.core-extensions
using import Array
using import struct
using import glm
using import property
import .wgpu
import .gfxstate

struct TextureQuad plain
    position : vec2
    extent : vec2

struct SpriteVertexAttributes plain
    position : vec2
    uv       : vec2
    color    : vec4

struct SpriteBatch
    vbuffer          : wgpu.BufferId
    ibuffer          : wgpu.BufferId
    # texture-bindings : wgpu.BindGroupId
    vertices         : (Array SpriteVertexAttributes)

    VertexAttributes := SpriteVertexAttributes
    SpriteLimit      := ((2 ** 16) // 6)
    MaxIndexCount    := (SpriteLimit * 6)
    IndexBufferSize  := ((sizeof u16) * MaxIndexCount)
    MaxVertexCount   := (SpriteLimit * 4)
    VertexBufferSize := ((sizeof VertexAttributes) * MaxVertexCount)

    vvv bind SpriteCount
    property
        inline "get" (self)
            (countof self.vertices) // 4

    inline __typecall (cls)
        # index buffer never changes (while we don't have batch resizing),
        # so we can just generate it at the start and leave it be.
        local indices = ((Array u16))
        'resize indices MaxIndexCount
        # fill in index data
        for i in (range SpriteLimit)
            let vertex-offset = (i * 4)
            let
                top-left     = vertex-offset
                top-right    = (vertex-offset + 1)
                bottom-left  = (vertex-offset + 2)
                bottom-right = (vertex-offset + 3)

            let index-offset = (i * 6)
            indices @ index-offset       = (top-left as u16)
            indices @ (index-offset + 1) = (bottom-left as u16)
            indices @ (index-offset + 2) = (bottom-right as u16)
            indices @ (index-offset + 3) = (bottom-right as u16)
            indices @ (index-offset + 4) = (top-right as u16)
            indices @ (index-offset + 5) = (top-left as u16)

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

        local vertices = ((Array VertexAttributes))
        let vbuffer-size =
            (sizeof VertexAttributes) * 4 * SpriteLimit
        print vbuffer-size
        let vbuffer =
            wgpu.device_create_buffer device
                &local wgpu.BufferDescriptor
                    label = "spritebatch vertex attributes"
                    size = vbuffer-size
                    usage =
                        wgpu.BufferUsage_COPY_DST | wgpu.BufferUsage_VERTEX

        local vertices = ((Array VertexAttributes))

        super-type.__typecall cls
            ibuffer = ibuffer
            vbuffer = vbuffer
            vertices = vertices

    fn draw (self render-pass)
        wgpu.queue_write_buffer gfxstate.istate.queue self.vbuffer 0
            (imply self.vertices pointer) as (pointer u8)
            (sizeof VertexAttributes) * (countof self.vertices)

        wgpu.render_pass_set_vertex_buffer render-pass 0 self.vbuffer 0 (sizeof VertexAttributes)
        wgpu.render_pass_set_index_buffer render-pass self.ibuffer 0 IndexBufferSize
        # 2 triangles per sprite
        let index-count = ((self.SpriteCount * 6:usize) as u32)
        wgpu.render_pass_draw_indexed render-pass index-count 1 0 0 0
        ;

    fn clear (self)
        'clear self.vertices
        ;
    fn... add (self, x : i32, y : i32, width : i32, height : i32, orientation : f32, texture-quad)
        using import math

        let id = ((countof self.vertices) // 4)
        # for now tint will always be white
        color := (vec4 1 1 1 1)

        let origin = (vec2 x y)
        let uvx uvy uvw uvh =
            texture-quad.position.x
            texture-quad.position.y
            texture-quad.extent.x
            texture-quad.extent.y
        'append self.vertices
            # top left
            VertexAttributes
                position = origin
                uv = (vec2 uvx (uvy + uvh))
                color = color
        'append self.vertices
            # top right
            VertexAttributes
                position = (origin + (2drotate (vec2 width 0) orientation))
                uv = (vec2 (uvx + uvw) (uvy + uvh))
                color = color
        'append self.vertices
            # bottom left
            VertexAttributes
                position = (origin + (2drotate (vec2 0 height) orientation))
                uv = (vec2 uvx uvy)
                color = color
        'append self.vertices
            # bottom right
            VertexAttributes
                position = (origin + (2drotate (vec2 width height) orientation))
                uv = (vec2 (uvx + uvw) uvy)
                color = color
        id
locals;
