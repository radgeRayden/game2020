using import radlib.libc
using import radlib.core-extensions
import .raydEngine.use
import .filesystem
import .image
import HID
let gfx  = (import gfx-wgpu)
let wgpu = (import foreign.wgpu-native)
let glfw = (import foreign.glfw)


let AppSettings = (import radlib.app-settings)
'set-symbol AppSettings 'AOT? true
load-library (module-dir .. "/dist/lib/libraydengine_d.dll")

set-globals!
    ..
        (globals)
        do
            inline assert (v text)
                let header = (include "assert.h")
                stdio.puts (text as rawstring)
                header.extern.assert v
            locals;

run-stage;

global app-window : (mutable pointer glfw.window)
global render-thread : pthread.t

fn get-window-handle ()
    deref app-window

fn get-gfx-state ()
    deref gfx.backend

global loading-finished : bool
fn signal-loading-finished ()
    loading-finished = true
    pthread.join render-thread null
    ;

fn loading-screen (userdata)
    let b = gfx.backend
    while (not loading-finished)
        let next-image = (wgpu.swap_chain_get_next_texture b.swap-chain)
        if (next-image.view_id == 0)
            continue;

        # begin render pass
        let cmd-encoder =
            wgpu.device_create_command_encoder b.device
                &local wgpu.CommandEncoderDescriptor
                    label = "command encoder"

        local color-attachments : wgpu.RenderPassColorAttachmentDescriptor
            attachment = next-image.view_id
            load_op = wgpu.LoadOp.Clear
            store_op = wgpu.StoreOp.Store
            clear_color =
                wgpu.Color
                    r = 0.017
                    g = 0.017
                    b = 0.017
                    a = 1.0

        let render-pass =
            wgpu.command_encoder_begin_render_pass cmd-encoder
                &local wgpu.RenderPassDescriptor
                    color_attachments = &color-attachments
                    color_attachments_length = 1
                    depth_stencil_attachment = null

        wgpu.render_pass_end_pass render-pass
        local cmdbuf = (wgpu.command_encoder_finish cmd-encoder null)
        # we don't have to think about this because there's only one queue ever
        # due to the current wgpu implementation
        let queue = (wgpu.device_get_default_queue b.device)

        wgpu.queue_submit queue &cmdbuf 1
        wgpu.swap_chain_present b.swap-chain
    nullof voidstar

fn dummyfn ()
    ;

fn main (argc argv)
    try
        let fscookie =
            filesystem.init argc argv "../assets"
    else
        exit 1

    let splash =
        try
            image.ImageData (filesystem.FileData ("splash.png" as rawstring))
        else
            exit 1

    glfw.SetErrorCallback
        fn "glfw-raise-error" (error-code error-text)
            returning void
            stdio.printf "%s" error-text
            exit 1

    glfw.Init;

    glfw.WindowHint glfw.GLFW_CLIENT_API glfw.GLFW_OPENGL_API
    glfw.WindowHint glfw.GLFW_DECORATED false
    glfw.WindowHint glfw.GLFW_VISIBLE false

    let splashW splashH = 500 300
    app-window =
        glfw.CreateWindow splashW splashH "Loading..." null null

    if (app-window == null)
        exit 1

    let mwidth mheight = (HID.window.monitor-size)
    glfw.SetWindowPos app-window ((mwidth // 2) - (splashW // 2)) ((mheight // 2) - (splashH // 2))

    gfx.init (HID.window.create-wgpu-surface app-window) splashW splashH

    glfw.ShowWindow app-window

    pthread.create &render-thread null loading-screen (inttoptr 0 voidstar)
    let sc_init = (extern 'sc_init (function void voidstar i32 (pointer rawstring)))
    let sc_main = (extern 'sc_main (function i32))

    sc_init (static-typify dummyfn) argc argv
    sc_main;

compile-object
    default-target-triple
    compiler-file-kind-object
    "./dist/bin/game.o"
    do
        let
            main = (static-typify main i32 (pointer rawstring))
            get-window-handle = (static-typify get-window-handle)
            signal-loading-finished = (static-typify signal-loading-finished)
            get-gfx-state = (static-typify get-gfx-state)

        locals;
    # 'dump-module

switch operating-system
case 'linux
    stdlib.system
        ..
            "cd ./dist/bin; "
            "gcc -g -o game game.o -I../../raydEngine/foreign/include "
            "-Wl,-rpath=\\$ORIGIN/../lib -Wl,-z,origin -Wl,-E -L../lib -lscopesrt -lraydengine -lasound -ldl -lpthread -lm -lX11"
    stdlib.system "rm ./dist/bin/game.o"
case 'windows
    stdlib.system
        ..
            "cd ./dist/bin; "
            "gcc -g -o game game.o -I../../raydEngine/foreign/include "
            "-Wl,-rpath=\\$ORIGIN/../lib -Wl,-z,origin -Wl,-E -L../lib -lscopesrt -lraydengine -lasound -ldl -lpthread -lm -lX11"
    stdlib.system "rm ./dist/bin/game.o"
default
    print "unsupported OS"
