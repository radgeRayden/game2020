let argc argv = (launch-args)
run-stage;

using import struct
using import Array
using import property

import .raydEngine.use
let AppSettings = (import radlib.app-settings)
let physfs      = (import foreign.physfs)

inline check-error ()
    let errcode = (physfs.getLastErrorCode)
    let errstring = (physfs.getErrorByCode errcode)

    if (errcode != physfs.PHYSFS_ERR_OK)
        static-if (AppSettings.AOT? or AppSettings.ReleaseMode?)
            using import radlib.libc
            stdio.puts errstring
            raise errcode
        else
            hide-traceback;
            error (string errstring)

fn... init (argc = argc, argv = argv, mountdir : rawstring = ".")
    if (not (physfs.init (argv @ 0)))
        check-error;

    physfs.mount mountdir "/" true
    check-error;
    typedef FilesystemLifetimeCookie :: (storageof Nothing)
        inline __typecall (cls)
            bitcast none this-type
        inline __drop (self)
            physfs.deinit;
            ;

    FilesystemLifetimeCookie;

fn shutdown ()
    physfs.deinit;

inline enumerate-files (path)
    let files = (physfs.enumerateFiles path)
    Generator
        inline "start" ()
            0
        inline "valid?" (index)
            (files != null) and ((files @ index) != null)
        inline "at" (index)
            static-if AppSettings.AOT?
                files @ index
            else
                string (files @ index)
        inline "next" (index)
            index + 1

fn load-file (path)
    let file-handle = (physfs.openRead path)
    if (file-handle == null)
        check-error;

    let size = (physfs.fileLength file-handle)
    local data : (Array u8)
    'resize data size
    let read = (physfs.readBytes file-handle data._items (size as u64))

    if (read < size)
        check-error;

    data

struct FileData
    data : (Array u8)

    filename :
        embed
            static-if (not AppSettings.AOT?)
                string
            else
                rawstring
    Text :=
        property
            inline "get" (self)
                'get-text self
    Size :=
        property
            inline "get" (self)
                (countof self.data)


    inline __imply (lhsT rhsT)
        static-if (rhsT < pointer)
            static-match rhsT
            case (pointer i8)
                inline (self)
                    (imply self.data pointer) as (pointer i8)
            case (pointer u8)
                inline (self)
                    (imply self.data pointer) as (pointer u8)
            default
                ;
    fn get-text (self)
        string
            ((imply self.data pointer) as (pointer i8))
            (countof self.data)

    inline __typecall (cls path)
        let data = (load-file path)
        static-if AppSettings.AOT?
            super-type.__typecall cls
                data = data
        else
            let m? start end = ('match? "^.+/" path)
            let path =
                ? m? (rslice path end) path
            super-type.__typecall cls
                data = data
                filename = path

do
    let init shutdown FileData enumerate-files
    locals;
