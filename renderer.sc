import HID
let wgpu = (import foreign.wgpu-native)
let gfx = (import gfx-wgpu)

fn init ()
    gfx.init (HID.window.create-wgpu-surface) width height

fn draw-frame ()
   
locals;
