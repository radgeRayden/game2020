using import radlib.core-extensions

using import enum
using import glm

import .raydEngine.use
import HID
import timer
import .gfxstate
import .wgpu
import .2dtools
import .image
import .filesystem
import .shader-bindings

# Platform code initialization
filesystem.init;
HID.init (HID.WindowOptions (visible? = true)) (HID.GfxAPI.WebGPU)
# basic keybindings for use during testing, will be abstracted later when I have a proper
# input handling module.
HID.on-key-event =
    fn "key-callback" (ev)
        using HID.keyboard
        if (keybind ev KeyModifier.ALT KeyCode.ENTER)
            HID.window.toggle-fullscreen;

        if (keybind ev KeyCode.ESCAPE)
            HID.window.close;

# module initialization
gfxstate.init;
let device = ('force-unwrap gfxstate.istate.device)

let tex-atlas-data =
    try
        image.ImageData (filesystem.FileData "assets/SpriteSheet_player.png")
    else
        exit 1

global tex-atlas =
    wgpu.device_create_texture device
        &local wgpu.TextureDescriptor
            label = "atlas"
            size =
                wgpu.Extent3d
                    width = tex-atlas-data.width
                    height = tex-atlas-data.height
                    depth = 1
            mip_level_count = 1
            sample_count = 1
            dimension = wgpu.TextureDimension.D2
            format = wgpu.TextureFormat.Rgba8Unorm
            usage =
                wgpu.TextureUsage_COPY_DST | wgpu.TextureUsage_SAMPLED

wgpu.queue_write_texture gfxstate.istate.queue
    &local wgpu.TextureCopyView
        texture = tex-atlas
        mip_level = 0
    tex-atlas-data.data
    tex-atlas-data.Size
    &local wgpu.TextureDataLayout
        offset = 0
        bytes_per_row = (4 * tex-atlas-data.width)
        rows_per_image = tex-atlas-data.height
    &local wgpu.Extent3d
        width = tex-atlas-data.width
        height = tex-atlas-data.height
        depth = 1

let atlas-tex-view =
    wgpu.texture_create_view tex-atlas
        &local wgpu.TextureViewDescriptor
            label = "atlas texview"
            format = wgpu.TextureFormat.Rgba8Unorm
            dimension = wgpu.TextureViewDimension.D2
            aspect = wgpu.TextureAspect.All
            base_mip_level = 0
            level_count = 1
            base_array_layer = 0
            array_layer_count = 1

let vertex-shader fragment-shader =
    do
        using import glsl
        using shader-bindings
        fn vertex ()
            using sprite2d-vs
            gl_Position = (transform * (vec4 vposition 0.0 1.0))
            vcolor.out = vcolor.in
            vtexcoord.out = vtexcoord.in

        fn fragment ()
            using sprite2d-fs
            fcolor = (vcolor * (texture (sampler2D diffuse-t diffuse-s) vtexcoord))

        let vsrc = (compile-spirv 'vertex (static-typify vertex))
        let vlen = ((countof vsrc) // 4)
        let vertex-module =
            wgpu.device_create_shader_module device
                &local wgpu.ShaderModuleDescriptor
                    code =
                        typeinit
                            bytes = (vsrc as rawstring as (pointer u32))
                            length = vlen
        let fsrc = (compile-spirv 'fragment (static-typify fragment))
        let flen = ((countof fsrc) // 4)
        let fragment-module =
            wgpu.device_create_shader_module device
                &local wgpu.ShaderModuleDescriptor
                    code =
                        typeinit
                            bytes = (fsrc as rawstring as (pointer u32))
                            length = flen

        _ vertex-module fragment-module

local bind-group-layouts =
    arrayof wgpu.BindGroupLayoutId
        wgpu.device_create_bind_group_layout device
            &local wgpu.BindGroupLayoutDescriptor
                label = "transform"
                entries =
                    &local wgpu.BindGroupLayoutEntry
                        binding = 0
                        visibility = wgpu.WGPUShaderStage_VERTEX
                        ty = wgpu.BindingType.UniformBuffer
                entries_length = 1
        wgpu.device_create_bind_group_layout device
            &local wgpu.BindGroupLayoutDescriptor
                label = "diffuse texture"
                entries =
                    &local
                        arrayof wgpu.BindGroupLayoutEntry
                            typeinit
                                binding = 0
                                visibility = wgpu.WGPUShaderStage_FRAGMENT
                                ty = wgpu.BindingType.SampledTexture
                                view_dimension = wgpu.TextureViewDimension.D2
                                texture_component_type =
                                    wgpu.TextureComponentType.Uint
                            typeinit
                                binding = 1
                                visibility = wgpu.WGPUShaderStage_FRAGMENT
                                ty = wgpu.BindingType.Sampler
                entries_length = 2

let sprite-pip-layout =
    wgpu.device_create_pipeline_layout device
        &local wgpu.PipelineLayoutDescriptor
            bind_group_layouts = (&bind-group-layouts as (pointer u64))
            bind_group_layouts_length = 2

let sprite-pipeline =
    wgpu.device_create_render_pipeline device
        &local wgpu.RenderPipelineDescriptor
            layout = sprite-pip-layout
            vertex_stage =
                typeinit
                    module = vertex-shader
                    entry_point = "main"
            fragment_stage =
                &local wgpu.ProgrammableStageDescriptor
                    module = fragment-shader
                    entry_point = "main"
            primitive_topology = wgpu.PrimitiveTopology.TriangleList
            rasterization_state =
                &local wgpu.RasterizationStateDescriptor
            color_states =
                # TODO: change to MSAA with resolve_target
                &local wgpu.ColorStateDescriptor
                    format = wgpu.TextureFormat.Bgra8UnormSrgb
                    alpha_blend =
                        typeinit
                            src_factor = wgpu.BlendFactor.One
                            dst_factor = wgpu.BlendFactor.Zero
                            operation = wgpu.BlendOperation.Add
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
                    vertex_buffers =
                        &local wgpu.VertexBufferLayoutDescriptor
                            array_stride = (sizeof 2dtools.SpriteVertexAttributes)
                            step_mode = wgpu.InputStepMode.Vertex
                            attributes =
                                &local
                                    arrayof wgpu.VertexAttributeDescriptor
                                        typeinit
                                            offset =
                                                (offsetof 2dtools.SpriteVertexAttributes 'position)
                                            format = wgpu.VertexFormat.Float2
                                            shader_location =
                                                shader-bindings.Sprite2DAttribute.Position
                                        typeinit
                                            offset =
                                                (offsetof 2dtools.SpriteVertexAttributes 'uv)
                                            format = wgpu.VertexFormat.Float2
                                            shader_location =
                                                shader-bindings.Sprite2DAttribute.TextureCoordinates
                                        typeinit
                                            offset =
                                                (offsetof 2dtools.SpriteVertexAttributes 'color)
                                            format = wgpu.VertexFormat.Float4
                                            shader_location =
                                                shader-bindings.Sprite2DAttribute.Color
                            attributes_length = 3
                    vertex_buffers_length = 1
            sample_count = 1
            sample_mask = 0xffffffff

global batch = (2dtools.SpriteBatch)

fn update (dt)
    let KeyCode = HID.keyboard.KeyCode
    if (HID.keyboard.down? KeyCode.LEFT)
    ;

fn draw ()
    let device = ('force-unwrap gfxstate.istate.device)
    let cmd-encoder = (wgpu.device_create_command_encoder device null)
    let swapchain-image =
        wgpu.swap_chain_get_next_texture gfxstate.istate.swap-chain
    if (swapchain-image.view_id == 0)
        gfxstate.update-render-area;
        return;

    let render-pass =
        wgpu.command_encoder_begin_render_pass cmd-encoder
            &local wgpu.RenderPassDescriptor
                color_attachments =
                    &local wgpu.RenderPassColorAttachmentDescriptor
                        attachment = swapchain-image.view_id
                        load_op = wgpu.LoadOp.Clear
                        store_op = wgpu.StoreOp.Store
                        clear_color = (wgpu.Color 0.017 0.017 0.017 1.0)
                color_attachments_length = 1

    'draw batch render-pass

    wgpu.render_pass_end_pass render-pass
    local cmdbuf = (wgpu.command_encoder_finish cmd-encoder null)
    wgpu.queue_submit gfxstate.istate.queue cmdbuf
    wgpu.swap_chain_present gfxstate.istate.swap-chain
    ;

global game-timer = (timer.Timer)
while (not (HID.window.received-quit-event?))
    HID.window.poll-events;
    'step game-timer
    update ('delta-time game-timer)
    draw;
