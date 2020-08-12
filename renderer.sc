using import radlib.core-extensions
let AppSettings = (import radlib.app-settings)
import HID
import .wgpu

using import Option
using import Array
using import Rc
using import glm
using import struct

struct GfxState
    surface : (Option wgpu.SurfaceId)
    adapter : (Option wgpu.AdapterId)
    device : (Option wgpu.DeviceId)
    swap-chain : wgpu.SwapChainId
    screen-resolve-target : (Option wgpu.TextureId)
    queue : wgpu.QueueId

global istate : GfxState

fn update-render-area ()
    let width height = (HID.window.size)
    let device surface = ('force-unwrap istate.device) ('force-unwrap istate.surface)
    istate.swap-chain =
        wgpu.device_create_swap_chain device surface
            &local wgpu.SwapChainDescriptor
                usage = wgpu.TextureUsage_OUTPUT_ATTACHMENT
                format = wgpu.TextureFormat.Bgra8UnormSrgb
                width = (width as u32)
                height = (height as u32)
                present_mode = wgpu.PresentMode.Fifo
    ;

fn init ()
    wgpu.set_log_level wgpu.LogLevel.Error
    wgpu.set_log_callback
        fn "gfx-log" (level msg)
            static-if AppSettings.AOT?
                using import radlib.libc
                stdio.printf "level: %d - %s\n" level msg
                ;
            else
                print "level:" level "-" (string msg)

    let surface = (HID.window.create-wgpu-surface)

    # adapter configuration
    # =====================
    local adapter : u64
    wgpu.request_adapter_async
        &local wgpu.RequestAdapterOptions
            power_preference = wgpu.PowerPreference.LowPower
            compatible_surface = surface
        | 2 4 8
        false
        # callback
        fn "on-adapter-available" (result adapterptr)
            # adapter = result
            # let statusptr = (bitcast statusptr (mutable pointer bool))
            adapterptr as:= (mutable pointer u64)
            @adapterptr = result
            ;
        &adapter

    # device configuration
    # =====================
    let device =
        wgpu.adapter_request_device adapter
            0 # extensions
            &local wgpu.CLimits
                max_bind_groups = wgpu.DEFAULT_BIND_GROUPS
            null

    istate.surface = surface
    istate.adapter = adapter
    istate.device = device
    # creates and sets the swap chain
    update-render-area;
    istate.queue = (wgpu.device_get_default_queue device)
    ;

fn frame ()
    let device = ('force-unwrap istate.device)
    let cmd-encoder = (wgpu.device_create_command_encoder device null)
    let swapchain-image =
        wgpu.swap_chain_get_next_texture istate.swap-chain
    if (swapchain-image.view_id == 0)
        update-render-area;
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
    wgpu.render_pass_end_pass render-pass
    local cmdbuf = (wgpu.command_encoder_finish cmd-encoder null)
    wgpu.queue_submit istate.queue cmdbuf
    wgpu.swap_chain_present istate.swap-chain

locals;
