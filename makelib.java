import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Iterator;

import omf.OMF_Segment;

/*
 * Created on Nov 21, 2006
 * Nov 21, 2006 4:11:31 PM
 * 
 * makelib
 *  manage OMF library
 * 
 * makelib [options] library file
 * -a : add to library (creating if necessary)
 * -d : delete from library
 * -x : extract from library 
 * -t : list files (short listing)
 * -v : list files (long listing)
 * -i : integrity check
 */

public class makelib
{
    static final int FLAG_A = 1;
    static final int FLAG_D = 2;
    //static final int FLAG_F = 4;
    static final int FLAG_I = 8;
    static final int FLAG_T = 16;
    static final int FLAG_X = 32;
    static final int FLAG_DX = 64;

    private static void usage()
    {
        System.out.println("makelib v 0.1");
        System.out.println("usage: makelib [options] library [file ...]");
        System.out.println("options:");

        System.out.println("\t-a             add files to library (creating library, if necessary)");
        System.out.println("\t-d             delete files from library");
        System.out.println("\t-x             extract files from library");
        System.out.println("\t-i             test library integrity");
        //System.out.println("\t-f             list library files");
        System.out.println("\t-t             list library contents");
        System.out.println("\t-v             be verbose");
        System.out.println("\t-h             help");        
    }
    
    private static void ListLib(OMF_Library lib, boolean verbose)
    {      
        System.out.println("Name                           File                 Size     Private");
        System.out.println("------------------------------ -------------------- -------- -------");
        
        Iterator <OMF_Library.SymbolRec> iter;
        iter = lib.Symbols();
        for (; iter.hasNext(); )
        {
            OMF_Library.SymbolRec s = iter.next();
            System.out.printf("%1$-30s %2$-20s 0x%3$06x %4$s\n",
                    s.SymbolName,
                    s.FileName,
                    s.Segment.Length(),
                    s.Private ? "true" : ""
                    );                 
        }
    }
    
    /*
     * make a list of symbols in the library, then open
     * the file as an OMF file and verify they correspond.
     * 
     */
    private static void CheckLib(OMF_Library lib, boolean verbose)
    {        
        /*
         * go through the library and verify the symbol names
         * and private flag are correct.
         * 
         * Also, this verifies no public symbols have the same name.
         */
        /*
        HashSet<String> set = new HashSet<String>();
        
        Iterator <Library.SymbolRec> iter;
        iter = lib.Symbols();
        for (; iter.hasNext(); )
        {
            Library.SymbolRec s = iter.next();
            OMF_Segment omf = s.Segment;
            if (!s.SymbolName.equals(omf.SegmentName()))
            {
                System.out.println("Symbol names do not match : "
                        + s.FileName + "::" + s.SymbolName 
                        + " " + omf.SegmentName());
            }
            if (s.Private != omf.Private())
            {
                System.out.println("Private flag does not match on "
                        + s.FileName + "::" + s.SymbolName);                
            }
            
            if (s.Private == false)
            {
               if (set.contains(s.SymbolName))           
                {
                    System.out.println("Duplicate symbol: " + s.SymbolName);
                }
               else set.add(s.SymbolName);
            }
        }
        */
        
        
        
        /*
         * verify no files have the same name.
         */
        /*
        set.clear();
        Iterator<Library.FileRec> fiter = lib.Files();
        for ( ; fiter.hasNext(); )
        {
            Library.FileRec f = fiter.next();
            if (f == null) continue;
            if (set.contains(f.FileName))
            {
                System.out.println("Duplicate file: " + f.FileName);
            }
            else
                set.add(f.FileName);
        }
        */
        
        /*
         * TODO -  Now we need to go through the OMF file 
         * and verify all segments are accounted for.
         */

    }
    
    private static boolean ExtractLib(OMF_Library lib, String[] args, boolean verbose)
    {
        ArrayList<OMF_Segment> segs;
        
        for (String s : args)
        {
            if (verbose) System.out.printf("makelib: extracting %1$s.\n", s);
            segs = lib.GetSegments(s);
            if (segs == null)
            {
                System.err.printf("makelib: %1$s not found.", s);
            }
            else
            {
                int segnum = 1;
                FileOutputStream f;
                try
                {
                    f = new FileOutputStream(s);
                    for (OMF_Segment seg : segs)
                    {
                        seg.SetSegmentNumber(segnum++);
                        if (!seg.Save(f))
                        {
                            System.err.printf("makelib: error extracting %1$s:%2$s.", 
                                    s, seg.SegmentName());; 
                            return false;
                        }
                    }
                }
                catch (FileNotFoundException e)
                {
                    // TODO -- should also signal not to delete if FLAG_DX
                    System.err.printf("makelib: unable to extract %1$s.", s); 
                    return false;
                }
            }
        }
        return true;
    }
    private static void DeleteLib(OMF_Library lib, String[] args, boolean verbose)
    {

        for (String s : args)
        {
            if (verbose) System.out.printf("makelib: deleting %1$s.\n", s);
            if (!lib.RemoveFile(s))
            {
                System.err.printf("makelib: %1$s not found.", s);
            }
        }           
    } 
    private static void AddLib(OMF_Library lib, String[] args, boolean verbose)
    {
        ArrayList<String> symbols = new ArrayList<String>();
        for (String s: args)
        {
            if (verbose)
            {
                System.out.printf("makelib: adding %1$s.\n", s);
            }
            int status = lib.AddFile(s,symbols);
            switch(status)
            {
            case OMF_Library.E_OK:
                break;
            case OMF_Library.E_DUPLICATE_FILE:
            case OMF_Library.E_DUPLICATE_SYMBOL:
            case OMF_Library.E_NOFILE:
            case OMF_Library.E_NOTOBJECT:
            case OMF_Library.E_NOTOMF:
            
            }
            
        }
    }
    
    
    
    /**
     * @param args
     */
    public static void main(String[] args)
    {
        boolean fVerbose = false;
        int flags = 0;

        
        GetOpt go = new GetOpt(args, "adxtivh");
        
        
        int c;
        while ((c = go.Next()) != -1)
        {
            switch(c)
            {
            case 'a':
                flags |= FLAG_A;
                break;
            case 'd':
                flags |= FLAG_D;
                break;
            case 'x':
                flags |= FLAG_X;
                break;
            case 'i':
                flags |= FLAG_I;
                break;
            case 't':
                flags |= FLAG_T;
                break;
            case 'v':
                fVerbose = true;
                break;
            case 'h':
            case '?':
            default:
                usage();
                return;
                //break;
            }
        }
        
        //
        args = go.CommandLine();
        go = null;
        String[] files = null;
        File f = null;
        
        /*
         * 1 (and only 1) bit of flags should be set.
         * the only exception is DX are both allowed to extract
         * and delete. 
         */
        if (flags == (FLAG_D | FLAG_X))
                flags = FLAG_DX;
        
        if (args.length == 0 || Integer.bitCount(flags) != 1)
        {
            usage();
            return;
        }
        f = new File(args[0]);
        
        // a d x flags require multi arguments, others do not.
        if ((flags & (FLAG_A | FLAG_D | FLAG_X | FLAG_DX)) == 0)
        {
            if (args.length != 1)
            {
                usage();
                return;
            }            
        }
        else
        {
            if (args.length == 1)
            {
                usage();
                return;
            }
            
            files = new String[args.length - 1];
            for (int i = 1; i < args.length; i++)
                files[i - 1] = args[i];
            
        }
        
        // TODO if flag != FLAG_A, verify file exists, open as lib?
        OMF_Library lib = OMF_Library.LoadFile(f);
        if (lib == null)
        {
            if (flags == FLAG_A) lib = new OMF_Library();
            else
            {
                System.err.printf("makelib: %1$s is not a valid library file.",
                        f.getName());
                return;
            }
        }
        
        
        
        switch(flags)
        {
        case FLAG_T:
            ListLib(lib, fVerbose);
            break;
        case FLAG_I:
            CheckLib(lib, fVerbose);
            break;
        case FLAG_X:
            ExtractLib(lib, files, fVerbose);
            break;
        case FLAG_DX:
            if (!ExtractLib(lib, files, fVerbose))
                return;
        case FLAG_D:
            DeleteLib(lib, files, fVerbose);
            if (lib.IsDirty())
                lib.Save(f);
            break;
        case FLAG_A:
            AddLib(lib, files, fVerbose);
            if (lib.IsDirty())
                lib.Save(f);
            break;
            
        }
        

        
    }

    
    
    
    
}
