let AppSettings = (import radlib.app-settings)
using import struct
using import enum
using import Array

import .raydEngine.use
let stbi = (import foreign.stbi)

enum ImageFormat plain
    RGBA8

fn... load-image (data : (Array u8), format)
    local channel-count : i32
    local width : i32
    local height : i32

    # NOTE: change this when supporting other image formats
    let desired-channels = 4

    let imgbytes =
        stbi.load_from_memory \
            data ((countof data) as i32) &width &height &channel-count desired-channels

    if (imgbytes == null)
        using import radlib.libc
        stdio.printf "%s %s" ("cannot load image:" as rawstring) (stbi.failure_reason)
        raise false

    let byte-array = (ptrtoref (alloca (Array u8)))
    byte-array._items = imgbytes
    let size =
        switch format
        case ImageFormat.RGBA8
            (width * height * channel-count)
        default
            raise false
    byte-array._count = size
    byte-array._capacity = byte-array._count

    _ byte-array width height channel-count


struct ImageData
    data : (Array u8)
    width : i32
    height : i32
    channel-count : i32
    format : ImageFormat

    inline __typecall (cls filedata)
        let data width height channels = (load-image filedata.data ImageFormat.RGBA8)
        super-type.__typecall cls
            data = data
            width = width
            height = height
            channel-count = channels
            format = ImageFormat.RGBA8

do
    let ImageFormat ImageData
    locals;
