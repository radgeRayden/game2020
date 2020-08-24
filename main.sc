using import radlib.core-extensions

using import enum
using import glm
using import struct

import .raydEngine.use
import math
import HID
import timer
import .gfxstate
import .wgpu
import .2dtools
import .image
import .filesystem
import .shader-bindings
import .gfx.descriptors

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
                    width = 45
                    height = 45
                    depth = 56
            mip_level_count = 1
            sample_count = 1
            dimension = wgpu.TextureDimension.D2
            format = wgpu.TextureFormat.Rgba8UnormSrgb
            usage =
                wgpu.TextureUsage_COPY_DST | wgpu.TextureUsage_SAMPLED

# values are hardcoded.
# The atlas is 360x315 px, and every array slice is 45x45.
for i in (range 56)
    i as:= u32
    let x = ((i % 8) * 45)
    let y = ((i // 8) * 45)
    # skips y rows in the data pointer
    let offset = ((4 * tex-atlas-data.width * y) + (4 * x))
    wgpu.queue_write_texture gfxstate.istate.queue
        &local wgpu.TextureCopyView
            texture = tex-atlas
            mip_level = 0
            origin =
                typeinit 0 0 i
        & (tex-atlas-data.data @ offset)
        # the byte size of one slice
        tex-atlas-data.Size
        &local wgpu.TextureDataLayout
            bytes_per_row = (tex-atlas-data.width * 4)
            rows_per_image = 45
        &local wgpu.Extent3d
            width = 45
            height = 45
            depth = 1

let atlas-tex-view =
    wgpu.texture_create_view tex-atlas
        &local wgpu.TextureViewDescriptor
            label = "atlas texview"
            format = wgpu.TextureFormat.Rgba8UnormSrgb
            dimension = wgpu.TextureViewDimension.D2
            aspect = wgpu.TextureAspect.All
            base_mip_level = 0
            level_count = 1
            base_array_layer = 0
            array_layer_count = 56

let atlas-sampler =
    wgpu.device_create_sampler device
        &local wgpu.SamplerDescriptor
            label = "diffuse sampler"
            address_mode_u = wgpu.AddressMode.ClampToEdge
            address_mode_v = wgpu.AddressMode.ClampToEdge
            address_mode_w = wgpu.AddressMode.ClampToEdge
            mag_filter = wgpu.FilterMode.Nearest
            min_filter = wgpu.FilterMode.Nearest
            mipmap_filter = wgpu.FilterMode.Nearest
            compare = wgpu.CompareFunction.Always

global transform-ubo =
    wgpu.device_create_buffer device
        &local wgpu.BufferDescriptor
            label = "transform uniform"
            size = (sizeof mat4)
            usage =
                wgpu.BufferUsage_COPY_DST | wgpu.BufferUsage_UNIFORM

let vertex-shader fragment-shader =
    do
        using import glsl
        using shader-bindings
        using math
        fn vertex ()
            using sprite2d-vs

            local vertices =
                arrayof vec2
                    vec2 0 0 # top left
                    vec2 1 0 # top right
                    vec2 0 1 # bottom left
                    vec2 1 1 # bottom right

            local texcoords =
                arrayof vec2
                    vec2 0 1
                    vec2 1 1
                    vec2 0 0
                    vec2 1 0


            # pivot reference:
                local v =
                {
                origin + pivot + _2drotate(v2(0,0) - pivot, r),
                origin + pivot + _2drotate(v2(0,h) - pivot, r),
                origin + pivot + _2drotate(v2(w,h) - pivot, r),
                origin + pivot + _2drotate(v2(w,0) - pivot, r),
                origin + pivot + _2drotate(v2(0,0) - pivot, r)
                }

            idx  := gl_VertexIndex
            sprite := (sprites @ (idx // 4))
            origin := sprite.position
            vertex := (vertices @ (idx % 4))
            orientation := sprite.rotation
            pivot := sprite.pivot

            gl_Position =
                * transform
                    vec4 (origin + pivot + (2drotate ((vertex * sprite.size) - pivot) orientation)) 0 1
            vcolor = sprite.color
            vtexcoord = (vec3 (texcoords @ (idx % 4)) sprite.layer)

        fn fragment ()
            using sprite2d-fs
            fcolor = (vcolor * (texture (sampler2DArray diffuse-t diffuse-s) vtexcoord))

        let vsrc = (compile-spirv 0x10000 'vertex (static-typify vertex))
        let vlen = ((countof vsrc) // 4)
        let vertex-module =
            wgpu.device_create_shader_module device
                &local wgpu.ShaderModuleDescriptor
                    code =
                        typeinit
                            bytes = (vsrc as rawstring as (pointer u32))
                            length = vlen
        let fsrc = (compile-spirv 0x10000 'fragment (static-typify fragment))
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
                                view_dimension = wgpu.TextureViewDimension.D2Array
                                texture_component_type =
                                    wgpu.TextureComponentType.Uint
                            typeinit
                                binding = 1
                                visibility = wgpu.WGPUShaderStage_FRAGMENT
                                ty = wgpu.BindingType.Sampler
                entries_length = 2
        wgpu.device_create_bind_group_layout device
            &local wgpu.BindGroupLayoutDescriptor
                label = "sprite data"
                entries =
                    &local wgpu.BindGroupLayoutEntry
                        binding = 0
                        visibility = wgpu.WGPUShaderStage_VERTEX
                        ty = wgpu.BindingType.StorageBuffer
                        storage_texture_format = wgpu.TextureFormat.R8Uint
                entries_length = 1


let sprite-pip-layout =
    wgpu.device_create_pipeline_layout device
        &local wgpu.PipelineLayoutDescriptor
            bind_group_layouts = ((& (view bind-group-layouts)) as (pointer u64))
            bind_group_layouts_length = (countof bind-group-layouts)

global sprite-pipeline =
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
                            src_factor = wgpu.BlendFactor.SrcAlpha
                            dst_factor = wgpu.BlendFactor.OneMinusSrcAlpha
                            operation = wgpu.BlendOperation.Add
                    color_blend =
                        typeinit
                            src_factor = wgpu.BlendFactor.SrcAlpha
                            dst_factor = wgpu.BlendFactor.OneMinusSrcAlpha
                            operation = wgpu.BlendOperation.Add
                    write_mask = wgpu.ColorWrite_ALL
            color_states_length = 1
            vertex_state =
                wgpu.VertexStateDescriptor
                    index_format = wgpu.IndexFormat.Uint16
            sample_count = 1
            sample_mask = 0xffffffff

local transform-binding =
    (gfx.descriptors.bindings.Buffer 0 transform-ubo 0 (sizeof mat4))

global transform-bgroup =
    wgpu.device_create_bind_group device
        &local wgpu.BindGroupDescriptor
            label = "transform bind group"
            layout = (bind-group-layouts @ 0)
            entries = &transform-binding
            entries_length = 1

global batch =
    2dtools.SpriteBatch
        wgpu.device_create_bind_group device
            &local wgpu.BindGroupDescriptor
                label = "atlas texture bind group"
                layout = (bind-group-layouts @ 1)
                entries =
                    &local
                        arrayof wgpu.BindGroupEntry
                            gfx.descriptors.bindings.TextureView 0 atlas-tex-view
                            gfx.descriptors.bindings.Sampler 1 atlas-sampler
                entries_length = 2
        view (bind-group-layouts @ 2)
struct Character plain
    position : vec2
global game-timer = (timer.Timer)
global character : Character
fn update (dt)
    let KeyCode = HID.keyboard.KeyCode
    let pos = character.position
    dt := (1 / 60)

    if (HID.keyboard.down? KeyCode.LEFT)
        pos.x -= (50 * dt)
    elseif (HID.keyboard.down? KeyCode.RIGHT)
        pos.x += (50 * dt)

    'clear batch
    'append batch.sprites
        shader-bindings.SpriteQuad
            position = pos
            rotation = (('run-time-real game-timer) as f32)
            size = (vec2 (45 * 3) (45 * 3))
            layer = 10
            color = (vec4 1)
            pivot = (vec2 ((45 * 3) // 2))
    'append batch.sprites
        shader-bindings.SpriteQuad
            position = pos
            rotation = 0
            size = (vec2 10)
            layer = 10
            color = (vec4 1)
            pivot = (vec2 0.5)
    'append batch.sprites
        shader-bindings.SpriteQuad
            position = pos
            rotation = 0
            size = (vec2 (45 * 3))
            layer = 10
            color = (vec4 1)
            pivot = (vec2 0.5)
    #batch (pos.x as i32) (pos.y as i32) (45 * 3) (45 * 3) (('run-time-real game-timer) as f32) 10:u32
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
                        clear_color = (wgpu.Color 1 1 1 1)
                color_attachments_length = 1

    wgpu.render_pass_set_pipeline render-pass sprite-pipeline

    let width height = (HID.window.size)
    local transform-data =
        math.transform.ortographic-projection width height true
    wgpu.queue_write_buffer gfxstate.istate.queue  transform-ubo 0
        &transform-data as (pointer u8)
        sizeof mat4

    wgpu.render_pass_set_bind_group render-pass
        \ shader-bindings.DescriptorSet.Transform transform-bgroup null 0
    'draw batch render-pass

    wgpu.render_pass_end_pass render-pass
    local cmdbuf = (wgpu.command_encoder_finish cmd-encoder null)
    wgpu.queue_submit gfxstate.istate.queue cmdbuf
    wgpu.swap_chain_present gfxstate.istate.swap-chain
    ;

while (not (HID.window.received-quit-event?))
    HID.window.poll-events;
    'step game-timer
    update ('delta-time game-timer)
    draw;
