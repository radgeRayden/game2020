using import radlib.core-extensions
using import Array
using import struct
import .wgpu
import .gfxstate

fn make-2d-pipeline ()

struct SpriteVertexAttributes plain
    position : vec2
    uv       : vec2
    color    : vec4

struct SpriteBatch
    vbuffer          : wgpu.BufferId
    ibuffer          : wgpu.BufferId
    texture-bindings : wgpu.BingGroupId
    vertices         : (Array SpriteVertexAttributes)

    VertexAttributes := SpriteVertexAttributes
    SpriteLimit      := ((2 ** 16) // 6)
    MaxIndexCount    := (SpriteLimit * 6)
    IndexBufferSize  := ((sizeof u16) * MaxIndexCount)
    MaxVertexCount   := (SpriteLimit * 4)
    VertexBufferSize := ((sizeof VertexAttributes) * MaxVertexCount)

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
        let vbuffer =
            wgpu.device_create_buffer device
                &local wgpu.BufferDescriptor
                    label = "spritebatch vertex attributes"
                    size = vbuffer-size
                    usage =
                        wgpu.BufferUsage_COPY_DST | wgpu.BufferUsage_VERTEX

        super-type.__typecall cls
            ibuffer = ibuffer
            vbuffer = vbuffer

    fn draw (self render-pass)
        ;
locals;
