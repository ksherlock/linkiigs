/*
 * Created on Feb 10, 2006
 * Feb 10, 2006 9:17:49 PM
 */

public class LinkError extends Exception
{
    // TODO -- put these in some sort of order based on severity.
    public static final int E_COMPLEX = 1;
    public static final int E_UNRESOLVED = 2;
    public static final int E_DUPLICATE = 3;
    public static final int E_BADOP = 4;
    public static final int E_RECURSIVE = 5;
    public static final int E_EXPRESSION = 6;
    public static final int E_BADFILE = 7;
    public static final int E_ZPEXPR = 8;
    public static final int E_BKEXPR = 9;
    public static final int E_RELEXPR = 10;
    public static final int E_NOEOF = 11;
    public static final int E_ALIGN_ORG = 12;
    public static final int E_ALIGN_FACTOR = 13;
    public static final int E_ALIGN_SEGMENT = 14;
    public static final int E_USING = 15;
    public static final int E_CODESIZE = 16;
    
    private static final long serialVersionUID = 1L;
    private int fError;
    private String fSegment;
    private int fOffset;
    private String fArg;
    
    
    public LinkError(int error, Symbol sym)
    {
        this(error, sym, null);
    }
    
    public LinkError(int error, Symbol sym, String arg)
    {
        fError = error;
        if (sym == null)
        {
          fSegment = null;
          fOffset = -1;
        }
        else
        {
            fSegment = sym.segmentname;
            fOffset = sym.offset;
        }
        fArg = arg;
    }
    public LinkError(int error)
    {
        this(error, null, null);
    }
    public LinkError(int error, String arg)
    {
        this(error, null, arg);
    }
    
    public boolean Fatal()
    {
        switch (fError)
        {
        case E_COMPLEX:
            return true;
        default: return false;
        }
    }
    public void SetSegment(String segment)
    {
        fSegment = segment;
    }
    public void SetOffset(int offset)
    {
        fOffset = offset;
    }
    public void SetArg(String arg)
    {
        fArg = arg;
    }
    public int Error()
    {
        return fError;
    }
    
    public String toString()
    {
        StringBuffer out = new StringBuffer();
        
        if (fSegment != null)
        {
            out.append(fSegment);
            if (fOffset != -1)
            {
                out.append('+');
                out.append(fOffset);
            }
            out.append(": ");
        }
        
        switch (fError)
        {
        case E_COMPLEX:
            out.append("Expression too complex.");
            break;
        case E_UNRESOLVED:
            out.append("Unresolved label:");
            break;
        case E_DUPLICATE:
            out.append("Duplicate label:");
            break;
        case E_BADOP:
            out.append("Invalid opcode:");
            break;
        case E_RECURSIVE:
            out.append("Expression is unresolvable.");
            break;
        case E_EXPRESSION:
            out.append("Unable to evaluate expression.");
            break;
        case E_BADFILE:
            out.append("Invalid OMF File:");
            break;
        case E_ZPEXPR:
            out.append("Address is not in zero page.");
            break;
        case E_BKEXPR:
            out.append("Address is not in current bank.");
            break;
        case E_RELEXPR:
            out.append("Relative address out of range.");
            break;
        case E_NOEOF:
            out.append("EOF opcode not found.");
            break;
        case E_ALIGN_ORG:
            out.append("Alignment and ORG conflict.");
            break;
        case E_ALIGN_FACTOR:
            out.append("Alignment factor must be a power of 2.");
            break;
        case E_ALIGN_SEGMENT:
            out.append("Alignment factor must not exceed segment alignment factor.");
            break;  
        case E_USING:
            out.append("Data Area not found:");
            break;
        case E_CODESIZE:
            out.append("Code exceeds code bank size.");
            break;
            
           default:
               out.append("Oops! ");
               out.append(fError);
               break;
        }
        if (fArg != null)
        {
            out.append(' ');
            out.append(fArg);
        }
        
        return out.toString();
    }
 
}
