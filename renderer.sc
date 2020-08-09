using import radlib.core-extensions
let AppSettings = (import radlib.app-settings)
import HID
import .gfx

using import Option
using import Array
using import Rc
using import glm
using import struct

struct GfxState
    surface : (Option gfx.SurfaceId)
    adapter : (Option gfx.AdapterId)
    device : (Option gfx.DeviceId)
    swap-chain : gfx.SwapChainId
    screen-resolve-target : (Option gfx.TextureId)
    queue : gfx.QueueId

global istate : GfxState

fn update-render-area ()
    let width height = (HID.window.size)
    let device surface = ('force-unwrap istate.device) ('force-unwrap istate.surface)
    istate.swap-chain =
        gfx.device_create_swap_chain device surface
            &local gfx.SwapChainDescriptor
                usage = gfx.TextureUsage_OUTPUT_ATTACHMENT
                format = gfx.TextureFormat.Bgra8UnormSrgb
                width = (width as u32)
                height = (height as u32)
                present_mode = gfx.PresentMode.Fifo
    ;

fn init ()
    gfx.set_log_level gfx.LogLevel.Error
    gfx.set_log_callback
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
    gfx.request_adapter_async
        &local gfx.RequestAdapterOptions
            power_preference = gfx.PowerPreference.LowPower
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
        gfx.adapter_request_device adapter
            0 # extensions
            &local gfx.CLimits
                max_bind_groups = gfx.DEFAULT_BIND_GROUPS
            null

    istate.surface = surface
    istate.adapter = adapter
    istate.device = device
    # creates and sets the swap chain
    update-render-area;
    istate.queue = (gfx.device_get_default_queue device)
    ;

fn frame ()
    let device = ('force-unwrap istate.device)
    let cmd-encoder = (gfx.device_create_command_encoder device null)
    let swapchain-image =
        gfx.swap_chain_get_next_texture istate.swap-chain
    if (swapchain-image.view_id == 0)
        update-render-area;
        return;

    let render-pass =
        gfx.command_encoder_begin_render_pass cmd-encoder
            &local gfx.RenderPassDescriptor
                color_attachments =
                    &local gfx.RenderPassColorAttachmentDescriptor
                        attachment = swapchain-image.view_id
                        load_op = gfx.LoadOp.Clear
                        store_op = gfx.StoreOp.Store
                        clear_color = (gfx.Color 0.017 0.017 0.017 1.0)
                color_attachments_length = 1
    gfx.render_pass_end_pass render-pass
    local cmdbuf = (gfx.command_encoder_finish cmd-encoder null)
    gfx.queue_submit istate.queue cmdbuf
    gfx.swap_chain_present istate.swap-chain

locals;
