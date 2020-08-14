using import radlib.core-extensions
import .raydEngine.use
import HID
import timer
import .gfxstate
import .wgpu
import .2dtools

# Platform code initialization
HID.init (HID.WindowOptions (visible? = true)) (HID.GfxAPI.WebGPU)

# module initialization
gfxstate.init;

# basic keybindings for use during testing, will be abstracted later when I have a proper
# input handling module.
HID.on-key-event =
    fn "key-callback" (ev)
        using HID.keyboard
        if (keybind ev KeyModifier.ALT KeyCode.ENTER)
            HID.window.toggle-fullscreen;

        if (keybind ev KeyCode.ESCAPE)
            HID.window.close;



fn update (dt)
    let KeyCode = HID.keyboard.KeyCode
    if (HID.keyboard.down? KeyCode.LEFT)
    ;

global batch = (2dtools.SpriteBatch)
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
