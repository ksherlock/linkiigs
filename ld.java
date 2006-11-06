/*
 * Main.java
 *
 * Created on December 21, 2005, 4:06 PM
 *
 * OMF linker
 */

import java.util.*;
//import omf.*;
import java.io.*;

/**
 *
 * @author Kelvin W Sherlock
 */
public class ld {
    
    /** Creates a new instance of Main */
    public ld() {
    }
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        
        int flags = Linker.FLAG_EXPRESS;
        int org = 0;
        boolean trace = false;
        
        String outf = "a.out";
        ArrayList<String> libs = new ArrayList<String>();
        ArrayList<String> libDirs = new ArrayList<String>();
       
        
        
        GetOpt go = new GetOpt(args, "hMrtvVxB:l:L:o:T:");

        int c;
        while ((c = go.Next()) != -1)
        {
            switch(c)
            {
            case 'B':
                {
                String s = go.Argument();
                if (s.compareToIgnoreCase("rtl") == 0)
                    flags |= Linker.FLAG_RTL;
                else if (s.compareToIgnoreCase("bin") == 0)
                    flags |= Linker.FLAG_BIN;
                }
                break;
            case 'l':
                libs.add(go.Argument());
                break;
            case 'L':
                libDirs.add(go.Argument());
                break;
            case 'M':
                 flags |= Linker.FLAG_MAP;
                 break;
            case 'o':
                outf = go.Argument();
                break;
            case 'r':
                flags |= Linker.FLAG_RELOAD;
                break;
            case 't':
                trace = true;
                break;
            case 'T':
                org = ParseNumber(go.Argument());
                break;
            case 'v':
                flags |= Linker.FLAG_VERBOSE;
                break;
            case 'x':
                flags &= (~Linker.FLAG_EXPRESS);
                break;               
            case 'h':
            case '?':
                usage();
                break;
            }
        }
        args = go.CommandLine();
        go = null;
        if (args.length == 0)
        {
            System.out.println("No input files.");
            return;
        }
        
        Linker linker = new Linker(flags, org);
        
        for (int i = 0; i < args.length; i++)
        {
            File f = new File(args[i]);
            if (trace) System.out.println(f.getPath());
            linker.ProcessFile(f);
        }
            
        
        // now add in any libraries...
        // consider having a default of ./ perhaps.
        for (Iterator<String> libIter = libs.iterator(); libIter.hasNext(); )
        {
            String lib = libIter.next();
            boolean found = false;
            
            for (Iterator<String> dirIter = libDirs.iterator(); dirIter.hasNext();)
            {
                String dir = dirIter.next();
                
                File f = new File(dir, lib);
                if (!f.exists()) continue;
                if (trace) System.out.println(f.getPath());
                linker.ProcessLibrary(f);
                found = true;
                break;
            }
            if (!found)
            {
                System.out.println("Library " + lib + " not found.");
            }
            
            
        }
        
        linker.Save(new File(outf));
        // now save 
    }
    
    private static int ParseNumber(String str)
    {
        int c;
        
        if (str == null) return 0;
        
        c = str.charAt(0);
        if (c == '$')
        {
            return Integer.parseInt(str.substring(1), 16);
        }
        if (c == '0' && str.charAt(1) == 'x')
        {
            return Integer.parseInt(str.substring(2), 16);
        }
        return Integer.parseInt(str);
    
    }
    
    private static void usage()
    {
        System.out.println("linkiigs v 0.1");
        System.out.println("usage: linkiigs [options] files");
        System.out.println("options:");
        System.out.println("\t-B bin         create binary file");
        System.out.println("\t-B rtl         create run time library file");
        System.out.println("\t-B omf         create omf file (default)");
        System.out.println("\t-h             show help");
        System.out.println("\t-l lib         library file to use");
        System.out.println("\t-L libpath     library path to use");
        System.out.println("\t-M             print link map");
        System.out.println("\t-o file        save output to file");
              
        //System.out.println("\t-p pathname    Pathname Table Segment path");
        System.out.println("\t-r             set reload bit in ~globals and ~arrays");
        System.out.println("\t-v             be verbose");
        System.out.println("\t-V             show version information");
        System.out.println("\t-T xxxx        absolute load segment address");
        System.out.println("\t-t             print the names of files as processed");
        System.out.println("\t-x             do NOT create ExpressLoad file");
        
       
    }
}
