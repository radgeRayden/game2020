using import radlib.core-extensions
switch operating-system
case 'linux
    load-library "libX11.so"
case 'windows
    ;
default
    ;

run-stage;

using import glm
using import glsl
using import struct
using import enum
using import Array
import .raydEngine.use
import HID
import .image
import .gfx-descriptor-prototypes

let gfx = (import gfx-wgpu)
let wgpu = (import foreign.wgpu-native)
using import math

# ================================================================================
enum AttributeLocation plain
    POSITION
    TEXCOORDS
    COLOR

struct VertexAttributes plain
    position : vec2
    texcoords : vec2
    color : vec4

fn white-1px-texture ()
    using import Option
    using import Array
    global white-texture : (Option gfx.2DTexture)
    if (not white-texture)
        local data : (Array u8)
        # set 4 bytes to 255, opaque white
        'resize data 4
        for b in data
            b = 0xFF
        let image-data =
            Struct.__typecall image.ImageData
                data = data
                width = 1
                height = 1
                channel-count = 4
                format = image.ImageFormat.RGBA8
        white-texture = (gfx.2DTexture image-data false "white-1px-texture")
    'force-unwrap white-texture

# ================================================================================

HID.init (HID.WindowOptions (visible? = false)) (HID.GfxAPI.WebGPU)

HID.on-window-resized =
    gfx.update-backbuffer-size
HID.on-key-event =
    fn "key-callback" (ev)
        using HID.keyboard
        if (keybind ev KeyModifier.ALT KeyCode.ENTER)
            HID.window.toggle-fullscreen;

        if (keybind ev KeyCode.ESCAPE)
            HID.window.close;

let width height = (HID.window.size)
gfx.init (HID.window.create-wgpu-surface) width height

HID.window.toggle-visible;

vvv bind vertex
gfx.Shader 'vertex
    fn ()
        using import glm
        using import glsl
        in position : vec2 (location = AttributeLocation.POSITION)
        inout texcoords : vec2 (location = AttributeLocation.TEXCOORDS)
        inout color : vec4 (location = AttributeLocation.COLOR)
        uniform mvp : (tuple mat4)
            set = 0
            binding = 0

        mvp := (extractvalue mvp 0)
        texcoords.out = texcoords.in
        color.out = color.in
        gl_Position = (mvp * (vec4 position 0.0 1))

vvv bind fragment
gfx.Shader 'fragment
    fn ()
        using import glm
        using import glsl
        in vcolor : vec4 (location = AttributeLocation.COLOR)
        in texcoords : vec2 (location = AttributeLocation.TEXCOORDS)
        out color : vec4 (location = 0)
        uniform diffuse-t : texture2D
            set = 1
            binding = 0
        uniform diffuse-s : sampler
            set = 1
            binding = 1

        color = (vcolor * (texture (sampler2D diffuse-t diffuse-s) texcoords))

# RENDER PIPELINE - NEEDS TO BE EXTRACTED
local texture-set-entries =
    arrayof wgpu.BindGroupLayoutEntry
        copy* gfx-descriptor-prototypes.binding-layouts.2d-sampled-texture
            binding = 0
        copy* gfx-descriptor-prototypes.binding-layouts.sampler
            binding = 1
            multisampled = true

local bind-group-layouts =
    arrayof wgpu.BindGroupLayoutId
        wgpu.device_create_bind_group_layout gfx.backend.device
            &local wgpu.BindGroupLayoutDescriptor
                label = "mvp set"
                entries =
                    &local wgpu.BindGroupLayoutEntry
                        binding = 0
                        visibility = wgpu.ShaderStage_VERTEX
                        ty = wgpu.BindingType.UniformBuffer
                entries_length = 1
        wgpu.device_create_bind_group_layout gfx.backend.device
            &local wgpu.BindGroupLayoutDescriptor
                label = "texture set"
                entries = &texture-set-entries
                entries_length = (countof texture-set-entries)

let mvp-uniform-buffer =
    wgpu.device_create_buffer gfx.backend.device
        &local wgpu.BufferDescriptor
            label = "mvp"
            size = (sizeof mat4)
            usage = gfx.UNIFORM_BUFFER_USAGE

let vtx-uniforms-bgroup =
    wgpu.device_create_bind_group gfx.backend.device
        &local wgpu.BindGroupDescriptor
            label = "vertex uniforms"
            layout = (bind-group-layouts @ 0)
            entries =
                &local
                    gfx-descriptor-prototypes.bindings.make-buffer-descriptor
                        binding = 0
                        buffer = mvp-uniform-buffer
                        offset = 0
                        size = (sizeof mvp-uniform-buffer)
            entries_length = 1

let notexture-view =
    wgpu.texture_create_view ('force-unwrap ((white-1px-texture) . handle))
        &local
            copy* gfx-descriptor-prototypes.objects.2d-texture-view
                label = "1px-white-texture-view"

let sprite-sampler =
    wgpu.device_create_sampler gfx.backend.device
        &local
            copy* gfx-descriptor-prototypes.objects.sprite-sampler
                label = "diffuse sampler"

local notexture-bgroup-entries =
    arrayof wgpu.BindGroupEntry
        gfx-descriptor-prototypes.bindings.make-texture-view-descriptor
            binding = 0
            texture-view = notexture-view
        gfx-descriptor-prototypes.bindings.make-sampler-descriptor
            binding = 1
            sampler-id = sprite-sampler

let notexture-bgroup =
    wgpu.device_create_bind_group gfx.backend.device
        &local wgpu.BindGroupDescriptor
            label = "notexture bind group"
            layout = (bind-group-layouts @ 1)
            entries = notexture-bgroup-entries
            entries_length = (countof notexture-bgroup-entries)

let rpip-layout =
    wgpu.device_create_pipeline_layout gfx.backend.device
        &local wgpu.PipelineLayoutDescriptor
            bind_group_layouts = &bind-group-layouts
            bind_group_layouts_length = (countof bind-group-layouts)

local vertex-attributes =
    arrayof wgpu.VertexAttributeDescriptor
        typeinit
            offset = (offsetof VertexAttributes 'position)
            format = wgpu.VertexFormat.Float2
            shader_location = AttributeLocation.POSITION
        typeinit
            offset = (offsetof VertexAttributes 'texcoords)
            format = wgpu.VertexFormat.Float2
            shader_location = AttributeLocation.TEXCOORDS
        typeinit
            offset = (offsetof VertexAttributes 'color)
            format = wgpu.VertexFormat.Float4
            shader_location = AttributeLocation.COLOR

local vertex-buffer-layouts =
    arrayof wgpu.VertexBufferLayoutDescriptor
        typeinit
            array_stride = (sizeof VertexAttributes)
            step_mode = wgpu.InputStepMode.Vertex
            attributes = vertex-attributes
            attributes_length = (countof vertex-attributes)

let screen-render-pip =
    gfx.RenderPipeline
        wgpu.device_create_render_pipeline gfx.backend.device
            &local wgpu.RenderPipelineDescriptor
                layout = rpip-layout
                vertex_stage =
                    wgpu.ProgrammableStageDescriptor
                        module = ('force-unwrap vertex.handle)
                        entry_point = "main"
                fragment_stage =
                    &local wgpu.ProgrammableStageDescriptor
                        module = ('force-unwrap fragment.handle)
                        entry_point = "main"
                primitive_topology = wgpu.PrimitiveTopology.TriangleList
                rasterization_state =
                    &local wgpu.RasterizationStateDescriptor
                sample_count = 1
                color_states =
                    &local wgpu.ColorStateDescriptor
                        format = wgpu.TextureFormat.Bgra8UnormSrgb
                        color_blend =
                            typeinit
                                src_factor = wgpu.BlendFactor.One
                                dst_factor = wgpu.BlendFactor.Zero
                                operation = wgpu.BlendOperation.Add
                        write_mask = wgpu.ColorWrite_ALL
                color_states_length = 1
                vertex_state =
                    wgpu.VertexStateDescriptor
                        index_format = wgpu.IndexFormat.Uint16
                        vertex_buffers = vertex-buffer-layouts
                        vertex_buffers_length = (countof vertex-buffer-layouts)

let internal-render-pip =
    gfx.RenderPipeline
        wgpu.device_create_render_pipeline gfx.backend.device
            &local wgpu.RenderPipelineDescriptor
                layout = rpip-layout
                vertex_stage =
                    wgpu.ProgrammableStageDescriptor
                        module = ('force-unwrap vertex.handle)
                        entry_point = "main"
                fragment_stage =
                    &local wgpu.ProgrammableStageDescriptor
                        module = ('force-unwrap fragment.handle)
                        entry_point = "main"
                primitive_topology = wgpu.PrimitiveTopology.TriangleList
                rasterization_state =
                    &local wgpu.RasterizationStateDescriptor
                sample_count = 4
                sample_mask = 0xFFFFFFFF
                color_states =
                    &local wgpu.ColorStateDescriptor
                        format = wgpu.TextureFormat.Rgba8Unorm
                        color_blend =
                            typeinit
                                src_factor = wgpu.BlendFactor.One
                                dst_factor = wgpu.BlendFactor.Zero
                                operation = wgpu.BlendOperation.Add
                        write_mask = wgpu.ColorWrite_ALL
                color_states_length = 1
                vertex_state =
                    wgpu.VertexStateDescriptor
                        index_format = wgpu.IndexFormat.Uint16
                        vertex_buffers = vertex-buffer-layouts
                        vertex_buffers_length = (countof vertex-buffer-layouts)

# END OF RENDER PIPELINE

let tri-vbuffer =
    wgpu.device_create_buffer gfx.backend.device
        &local wgpu.BufferDescriptor
            label = "triangle attributes"
            size = ((sizeof VertexAttributes) * 3)
            usage = gfx.VERTEX_BUFFER_USAGE

local tri-vertices =
    arrayof VertexAttributes
        typeinit
            position = (vec2 -0.5 -0.5)
            texcoords = (vec2 0.0 0.0)
            color = (vec4 1.0 1.0 1.0 1.0)
        typeinit
            position = (vec2 0.5 -0.5)
            texcoords = (vec2 1.0 0.0)
            color = (vec4 1.0 1.0 1.0 1.0)
        typeinit
            position = (vec2 0.0  0.5)
            texcoords = (vec2 0.5 1.0)
            color = (vec4 1.0 1.0 1.0 1.0)

wgpu.queue_write_buffer (wgpu.device_get_default_queue gfx.backend.device)
    tri-vbuffer
    0
    &tri-vertices as voidstar as (mutable pointer u8)
    sizeof tri-vertices

let screen-quad-vbuffer =
    wgpu.device_create_buffer gfx.backend.device
        &local wgpu.BufferDescriptor
            label = "screen quad attributes"
            size = ((sizeof VertexAttributes) * 6)
            usage = gfx.VERTEX_BUFFER_USAGE

local screen-quad-vertices =
    arrayof VertexAttributes
        typeinit
            position = (vec2 -1.0 1.0)
            texcoords = (vec2 0 0)
            color = (vec4 1.0 1.0 1.0 1.0)
        typeinit
            position = (vec2 -1.0 -1.0)
            texcoords = (vec2 0 1)
            color = (vec4 1.0 1.0 1.0 1.0)
        typeinit
            position = (vec2 1.0  -1.0)
            texcoords = (vec2 1 1)
            color = (vec4 1.0 1.0 1.0 1.0)
        typeinit
            position = (vec2 1.0  -1.0)
            texcoords = (vec2 1 1)
            color = (vec4 1.0 1.0 1.0 1.0)
        typeinit
            position = (vec2 1.0  1.0)
            texcoords = (vec2 1 0)
            color = (vec4 1.0 1.0 1.0 1.0)
        typeinit
            position = (vec2 -1.0 1.0)
            texcoords = (vec2 0 0)
            color = (vec4 1.0 1.0 1.0 1.0)

wgpu.queue_write_buffer (wgpu.device_get_default_queue gfx.backend.device)
    screen-quad-vbuffer
    0
    &screen-quad-vertices as voidstar as (mutable pointer u8)
    sizeof screen-quad-vertices

# SCREEN RENDER TARGET
# texture for the render target, and a pipeline that just samples and draws to a fullscreen quad.
let window-width window-height = (HID.window.size)
let screen-render-target =
    gfx.2DTexture
        window-width as u32
        window-height as u32
        true
        "screen render target"

let scr-render-view =
    wgpu.texture_create_view ('force-unwrap screen-render-target.handle)
        &local
            copy* gfx-descriptor-prototypes.objects.2d-texture-view
                label = "screen target"

local scr-render-bgroup-entries =
    arrayof wgpu.BindGroupEntry
        gfx-descriptor-prototypes.bindings.make-texture-view-descriptor
            binding = 0
            texture-view = scr-render-view
        gfx-descriptor-prototypes.bindings.make-sampler-descriptor
            binding = 1
            sampler-id = sprite-sampler

let scr-render-bgroup =
    wgpu.device_create_bind_group gfx.backend.device
        &local wgpu.BindGroupDescriptor
            label = "notexture bind group"
            layout = (bind-group-layouts @ 1)
            entries = scr-render-bgroup-entries
            entries_length = (countof scr-render-bgroup-entries)

while (not (HID.window.received-quit-event?))
    HID.window.poll-events;
    local cmd-encoder = (gfx.CommandEncoder)

    local cmd-encoder = (gfx.CommandEncoder)
    let screen-render-pass =
        try
            # local rp = (gfx.screen-pass cmd-encoder (vec4 0.017 0.017 0.017 1.0))
            let handle =
                wgpu.command_encoder_begin_render_pass ('force-unwrap cmd-encoder.handle)
                    &local wgpu.RenderPassDescriptor
                        color_attachments =
                            &local wgpu.RenderPassColorAttachmentDescriptor
                                attachment = scr-render-view
                                resolve_target = ((gfx.acquire-backbuffer) . view_id)
                                load_op = wgpu.LoadOp.Clear
                                store_op = wgpu.StoreOp.Store
                                clear_color = (wgpu.Color 0.017 0.017 0.017 1.0)
                        color_attachments_length = 1
                        depth_stencil_attachment = null
            local rp =
                gfx.RenderPass
                    handle = handle

        else
            continue;
    'set-pipeline screen-render-pass internal-render-pip
    let rpass = ('force-unwrap screen-render-pass.handle)
    wgpu.render_pass_set_vertex_buffer rpass 0 tri-vbuffer 0 0
    wgpu.render_pass_set_bind_group rpass 0 vtx-uniforms-bgroup null 0
    wgpu.render_pass_set_bind_group rpass 1 notexture-bgroup null 0

    let window-width window-height = (HID.window.size)

    local camera-mvp =
        *
            # set origin to top left corner
            transform.translation (vec3 -1 -1 0)
            transform.ortographic-projection window-width window-height true
            transform.translation (vec3 300 300 0)
            transform.scaling (vec3 300 300 1)
    wgpu.queue_write_buffer (wgpu.device_get_default_queue gfx.backend.device)
        mvp-uniform-buffer
        0
        &camera-mvp as voidstar as (mutable pointer u8)
        sizeof camera-mvp

    'draw screen-render-pass 3 1 0 0

    'finish screen-render-pass
    local cmdbuf = ('finish cmd-encoder)
    'submit cmdbuf

    gfx.present;
;
