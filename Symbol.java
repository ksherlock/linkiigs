import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ListIterator;

import omf.*;

/*
 * Created on Feb 4, 2006
 * Feb 4, 2006 1:28:12 PM
 */

/**
 * @author Kelvin Feb 4, 2006 1:28:12 PM
 */
public class Symbol
{

    public ArrayList expression;

    public ArrayList<String> using;

    public OMF_Segment segment;

    public String segmentname;

    public int location;

    public int offset;

    public int size;

    public int type;

    public int displacement;

    public int file;

    public OMF_Data data;

    private int fState;

    private SymbolMath fReduced;

    private SymbolTable fLocal;

    private SymbolTable fGlobal;

    static private final int STATE_UNKNOWN = 0;

    static private final int STATE_REDUCING = 1;

    static private final int STATE_REDUCED = 2;

    static private final int STATE_ERROR = -1;

    public Symbol(SymbolTable local, SymbolTable global)
    {
        fState = STATE_UNKNOWN;
        fLocal = local;
        fGlobal = global;
        fReduced = null;
        data = null;
        displacement = 0;
        type = 0;
        size = 0;
        offset = 0;
        location = 0;
        file = 1;
        segmentname = null;
        using = null;
        expression = null;

    }

    private void Error(int error, String arg) throws LinkError
    {
        fReduced = new SymbolMath(0);
        fState = STATE_ERROR;
        throw new LinkError(error, this, arg);
    }

    /*
     * return a list of missing labels.
     */
    public void FindMissing(HashSet<String> out)
    {
        if (expression != null)
        {
            ListIterator iter;
            iter = expression.listIterator();
            while (iter.hasNext())
            {
                Object o = iter.next();
                if (o instanceof OMF_Label)
                {
                    OMF_Label lab = (OMF_Label) o;
                    String name = lab.toString();

                    int opcode = lab.Opcode();
                    if (opcode == OMF_Expression.EXPR_COUNT)
                        continue;
                    if (opcode == OMF_Expression.EXPR_WEAK)
                        continue;
                    try
                    {
                        Symbol sym;
                        sym = fLocal.FindEntry(name, using);
                        if (sym != null)
                            continue;
                        sym = fGlobal.FindEntry(name, using);
                        if (sym != null)
                            continue;
                        out.add(name);

                    }
                    catch (LinkError e)
                    {
                    }
                }
            }
        }
    }

    /*
     * reduce a symbol to a constant or a label. throws an error if the symbol
     * can't be reduced (undefined, too complex, or recursive).
     */
    public SymbolMath Reduce() throws LinkError
    {
        if (fState == STATE_REDUCED || fState == STATE_ERROR)
            return fReduced;
        if (fState == STATE_REDUCING)
        {
            // oops - recursive.
            // throw an error.
            Error(LinkError.E_RECURSIVE, null);
        }

        // if expression is null, then this is a label and the value is the
        // location.
        if (expression == null)
        {
            fState = STATE_REDUCED;
            fReduced = new SymbolLabel(location, segment, file);
            return fReduced;
        }
        // otherwise, this is an expression that must be evaluated.

        Stack<SymbolMath> stack = new Stack<SymbolMath>();

        fState = STATE_REDUCING;

        // now evaluate the expression...

        ListIterator iter;
        iter = expression.listIterator();
        while (iter.hasNext())
        {
            Object o = iter.next();

            if (o instanceof OMF_Label)
            {
                OMF_Label lab = (OMF_Label) o;
                String name = lab.toString();

                Symbol sym = fLocal.FindEntry(name, using);

                if (sym == null)
                {
                    sym = fGlobal.FindEntry(name, using);
                }
                // TODO - check using.

                // sym is either an OMF_Local or OMF_Equ.
                // if it's an Equ, we need to reduce the expression.
                // WEAK and COUNT operations don't care if the symbol is
                // undefined.

                switch (lab.Opcode()) {
                case OMF_Expression.EXPR_COUNT:
                    stack.push(new SymbolMath(sym == null ? 0 : 1));
                    continue;
                case OMF_Expression.EXPR_WEAK:
                    // not an error.
                    if (sym == null)
                    {
                        stack.push(new SymbolMath(0));
                        continue;
                    } else
                    {
                        stack.push(sym.Reduce().Copy());
                    }
                    continue;
                }

                if (sym == null)
                {
                    Error(LinkError.E_UNRESOLVED, name);
                }

                switch (lab.Opcode()) {
                case OMF_Expression.EXPR_LEN:
                    stack.push(new SymbolMath(sym.size));
                    break;
                case OMF_Expression.EXPR_LABEL:
                    stack.push(sym.Reduce().Copy());
                    continue;
                case OMF_Expression.EXPR_TYPE:
                    stack.push(new SymbolMath(sym.type));
                    break;
                default:
                    Error(LinkError.E_BADOP, Integer.toString(lab.Opcode()));
                }
                continue;
            }
            if (o instanceof OMF_Number)
            {
                OMF_Number num = (OMF_Number) o;
                switch (num.Opcode()) {
                case OMF_Expression.EXPR_CLC:
                    // TODO - verify this is correct
                    stack.push(new SymbolMath(location));
                    break;

                case OMF_Expression.EXPR_ABS:
                    stack.push(new SymbolMath(num.Value()));
                    break;
                case OMF_Expression.EXPR_REL:
                    // convert to
                    stack.push(new SymbolLabel(location - offset + num.Value(),
                            segment, file));
                    // TODO - verify this is correct
                    // stack.push(new SymbolMath(location - offset +
                    // num.Value()));
                    break;
                default:
                    Error(LinkError.E_BADOP, Integer.toString(num.Opcode()));
                }
            }
            // integer signifies a mathematical operator
            if (o instanceof Integer)
            {
                SymbolMath s1, s2;
                boolean err = false;
                int val = ((Integer) o).intValue();
                if (val == OMF_Expression.EXPR_END)
                    break;
                /*
                 * the stack operations and math operations will throw an
                 * exception, so this code will catch and rethrow.
                 */
                try
                {
                    switch (val) {
                    // negate the value
                    case OMF_Expression.EXPR_NEG:
                        s1 = stack.pop();
                        stack.push(s1.negate());
                        break;
                    case OMF_Expression.EXPR_ADD:
                        s1 = stack.pop();
                        s2 = stack.pop();
                        stack.push(s2.add(s1));
                        break;
                    case OMF_Expression.EXPR_SUB:
                        s1 = stack.pop();
                        s2 = stack.pop();
                        stack.push(s2.subtract(s1));
                        break;
                    case OMF_Expression.EXPR_MUL:
                        s1 = stack.pop();
                        s2 = stack.pop();
                        stack.push(s2.multiply(s1));
                        break;
                    case OMF_Expression.EXPR_DIV:
                        s1 = stack.pop();
                        s2 = stack.pop();
                        stack.push(s2.divide(s1));
                        break;
                    case OMF_Expression.EXPR_MOD:
                        s1 = stack.pop();
                        s2 = stack.pop();
                        stack.push(s2.modulo(s1));
                        break;
                    case OMF_Expression.EXPR_SHIFT:
                        s1 = stack.pop();
                        s2 = stack.pop();
                        stack.push(s2.bit_shift(s1));
                        break;
                    case OMF_Expression.EXPR_BAND:
                        s1 = stack.pop();
                        s2 = stack.pop();
                        stack.push(s2.bit_and(s1));
                        break;
                    case OMF_Expression.EXPR_BOR:
                        s1 = stack.pop();
                        s2 = stack.pop();
                        stack.push(s2.bit_or(s1));
                        break;
                    case OMF_Expression.EXPR_BNOT:
                        s1 = stack.pop();
                        s2 = stack.pop();
                        stack.push(s2.bit_not(s1));
                        break;
                    case OMF_Expression.EXPR_BEOR:
                        s1 = stack.pop();
                        s2 = stack.pop();
                        stack.push(s2.bit_eor(s1));
                        break;
                    case OMF_Expression.EXPR_LAND:
                        s1 = stack.pop();
                        s2 = stack.pop();
                        stack.push(s2.logical_and(s1));
                        break;
                    case OMF_Expression.EXPR_LOR:
                        s1 = stack.pop();
                        s2 = stack.pop();
                        stack.push(s2.logical_or(s1));
                        break;
                    case OMF_Expression.EXPR_LNOT:
                        s1 = stack.pop();
                        s2 = stack.pop();
                        stack.push(s2.logical_not(s1));
                        break;
                    case OMF_Expression.EXPR_LEOR:
                        s1 = stack.pop();
                        s2 = stack.pop();
                        stack.push(s2.logical_eor(s1));
                        break;
                    case OMF_Expression.EXPR_GT:
                        s1 = stack.pop();
                        s2 = stack.pop();
                        stack.push(s2.logical_gt(s1));
                        break;
                    case OMF_Expression.EXPR_LT:
                        s1 = stack.pop();
                        s2 = stack.pop();
                        stack.push(s2.logical_lt(s1));
                        break;
                    case OMF_Expression.EXPR_GE:
                        s1 = stack.pop();
                        s2 = stack.pop();
                        stack.push(s2.logical_ge(s1));
                        break;
                    case OMF_Expression.EXPR_LE:
                        s1 = stack.pop();
                        s2 = stack.pop();
                        stack.push(s2.logical_le(s1));
                        break;
                    case OMF_Expression.EXPR_EQ:
                        s1 = stack.pop();
                        s2 = stack.pop();
                        stack.push(s2.logical_eq(s1));
                        break;
                    case OMF_Expression.EXPR_NE:
                        s1 = stack.pop();
                        s2 = stack.pop();
                        stack.push(s2.logical_ne(s1));
                        break;
                    default:
                        err = true;

                    }
                }
                catch (Exception e)
                {
                    // TODO -- check if stack or math error
                    Error(LinkError.E_COMPLEX, null);
                }
                if (err)
                {
                    Error(LinkError.E_BADOP, Integer.toString(val));
                }
            }

        }
        // done iterating through the expression.
        // at this point, stack should have 1 item, otherwise it's an error.
        if (stack.size() != 1)
            Error(LinkError.E_EXPRESSION, null);

        fReduced = stack.remove(0);

        fState = STATE_REDUCED;
        return fReduced;
    }

}

// TODO -- if labels are in different segments, should convert to a SymbolLabel.

class SymbolMath implements Cloneable
{
    public int value;

    public SymbolMath(Integer i)
    {
        value = i.intValue();
    }

    public SymbolMath(int v)
    {
        value = v;
    }

    public int Value()
    {
        return value;
    }

    public int Shift()
    {
        return 0;
    }

    public boolean isReloc()
    {
        return false;
    }

    public OMF_Relocation Reloc(OMF_Segment omf, int size, int offset)
    {
        return null;
    }

    public Object clone()
    {
        try
        {
            return super.clone();
        }
        catch (Exception e)
        {
            return null;
        }
    }

    // might be better to have a refCount variable.
    // retain would just increment the refcount
    // add/subtract/etc would check if refcount > 0, and
    // return a new copy in that case.
    // could reduce extra copies.
    public SymbolMath Copy()
    {
        return (SymbolMath) clone();
    }

    public SymbolMath add(SymbolMath sym) throws Exception
    {
        if (sym instanceof SymbolLabel)
            return sym.add(this);

        value += sym.value;
        return this;
    }

    public SymbolMath subtract(SymbolMath sym) throws Exception
    {
        // slightly incorrect, but it's easier to handle it here.
        if (sym instanceof SymbolLabel)
        {
            // eg 5-label .... weird but valid.
            SymbolLabel l = (SymbolLabel) sym;
            if (l.Shift() != 0)
                throw new Exception();
            l.value = value - l.value;
            return sym;
        }
        value -= sym.value;
        return this;
    }

    public SymbolMath multiply(SymbolMath sym) throws Exception
    {
        if (sym instanceof SymbolLabel)
            throw new Exception();
        value *= sym.value;
        return this;
    }

    public SymbolMath divide(SymbolMath sym) throws Exception
    {
        if (sym instanceof SymbolLabel)
            throw new Exception();
        value /= sym.value;
        return this;
    }

    public SymbolMath modulo(SymbolMath sym) throws Exception
    {
        if (sym instanceof SymbolLabel)
            throw new Exception();
        value %= sym.value;
        return this;
    }

    public SymbolMath bit_shift(SymbolMath sym) throws Exception
    {
        if (sym instanceof SymbolLabel)
            throw new Exception();

        if (sym.value > 0)
            value >>= sym.value;
        else
        {
            value <<= (-sym.value);
        }
        return this;
    }

    public SymbolMath bit_and(SymbolMath sym) throws Exception
    {
        if (sym instanceof SymbolLabel)
            throw new Exception();
        value &= sym.value;
        return this;
    }

    public SymbolMath bit_or(SymbolMath sym) throws Exception
    {
        if (sym instanceof SymbolLabel)
            throw new Exception();
        value |= sym.value;
        return this;
    }

    public SymbolMath bit_eor(SymbolMath sym) throws Exception
    {
        if (sym instanceof SymbolLabel)
            throw new Exception();
        value ^= sym.value;
        return this;
    }

    public SymbolMath bit_not(SymbolMath sym) throws Exception
    {
        if (sym instanceof SymbolLabel)
            throw new Exception();
        value &= ~sym.value;
        return this;
    }

    public SymbolMath negate() throws Exception
    {
        value = -value;
        return this;
    }

    public SymbolMath logical_and(SymbolMath sym) throws Exception
    {
        if (sym instanceof SymbolLabel)
            throw new Exception();
        value = ((value != 0) && (sym.value != 0)) ? 1 : 0;
        return this;
    }

    public SymbolMath logical_or(SymbolMath sym) throws Exception
    {
        if (sym instanceof SymbolLabel)
            throw new Exception();
        value = ((value != 0) || (sym.value != 0)) ? 1 : 0;
        return this;
    }

    public SymbolMath logical_eor(SymbolMath sym) throws Exception
    {
        if (sym instanceof SymbolLabel)
            throw new Exception();
        if (value == 0)
        {
            value = (sym.value == 0) ? 0 : 1;
        } else
        {
            value = (sym.value == 0) ? 1 : 0;
        }
        return this;
    }

    public SymbolMath logical_not(SymbolMath sym) throws Exception
    {
        if (sym instanceof SymbolLabel)
            throw new Exception();
        value = (value != sym.value) ? 1 : 0;
        return this;
    }

    public SymbolMath logical_gt(SymbolMath sym) throws Exception
    {
        if (sym instanceof SymbolLabel)
            throw new Exception();
        value = (value > sym.value) ? 1 : 0;
        return this;
    }

    public SymbolMath logical_lt(SymbolMath sym) throws Exception
    {
        if (sym instanceof SymbolLabel)
            throw new Exception();
        value = (value < sym.value) ? 1 : 0;
        return this;
    }

    public SymbolMath logical_eq(SymbolMath sym) throws Exception
    {
        if (sym instanceof SymbolLabel)
            throw new Exception();
        value = (value == sym.value) ? 1 : 0;
        return this;
    }

    public SymbolMath logical_ne(SymbolMath sym) throws Exception
    {
        if (sym instanceof SymbolLabel)
            throw new Exception();
        value = (value != sym.value) ? 1 : 0;
        return this;
    }

    public SymbolMath logical_ge(SymbolMath sym) throws Exception
    {
        if (sym instanceof SymbolLabel)
            throw new Exception();
        value = (value >= sym.value) ? 1 : 0;
        return this;
    }

    public SymbolMath logical_le(SymbolMath sym) throws Exception
    {
        if (sym instanceof SymbolLabel)
            throw new Exception();
        value = (value <= sym.value) ? 1 : 0;
        return this;
    }
}

class SymbolLabel extends SymbolMath
{
    public OMF_Segment segment;

    public int fShift;
    private int fFile;

    public SymbolLabel(int v, OMF_Segment seg, int file)
    {
        super(v);
        segment = seg;
        fShift = 0;
        fFile = file;
    }
 

    public int Shift()
    {
        return fShift;
    }

    public boolean isReloc()
    {
        return true;
    }

    /**
     * return a relocation record. will be either OMF_Reloc of OMF_Interseg,
     */
    public OMF_Relocation Reloc(OMF_Segment seg, int size, int offset)
    {
        if (seg == segment)
        {
            return new OMF_Reloc(size, fShift, offset, value);
        }
        return new OMF_Interseg(size, fShift, offset, seg.File(),
                segment.SegmentNumber(), value);
        // return null;
    }

    // these operations are supported:
    public SymbolMath add(SymbolMath sym) throws Exception
    {
        // can't modify if it's shifted.
        if (fShift != 0)
            throw new Exception();
        if (sym instanceof SymbolLabel)
        {
            SymbolLabel l = (SymbolLabel) sym;
            // can only add if it's in the same segment.
            if (l.segment != segment)
                throw new Exception();
            if (l.fShift != 0)
                throw new Exception();
            value += l.value;

        } else
            value += sym.value;
        return this;
    }

    public SymbolMath subtract(SymbolMath sym) throws Exception
    {
        // can't modify if it's shifted.
        if (fShift != 0)
            throw new Exception();
        if (sym instanceof SymbolLabel)
        {
            SymbolLabel l = (SymbolLabel) sym;
            // can only add if it's in the same segment.
            if (l.segment != segment)
                throw new Exception();
            if (l.fShift != 0)
                throw new Exception();
            value -= l.value;

        } else
            value -= sym.value;
        return this;
    }

    public SymbolMath bit_shift(SymbolMath sym) throws Exception
    {
        if (sym instanceof SymbolLabel)
            throw new Exception();

        fShift += sym.value;

        return this;
    }

    // these ones are not.

    public SymbolMath multiply(SymbolMath sym) throws Exception
    {
        // for powers of 2, we could shift...but orca doesn't.
        throw new Exception();
    }

    public SymbolMath divide(SymbolMath sym) throws Exception
    {
        throw new Exception();
    }

    public SymbolMath modulo(SymbolMath sym) throws Exception
    {
        throw new Exception();
    }

    public SymbolMath bit_and(SymbolMath sym) throws Exception
    {
        throw new Exception();
    }

    public SymbolMath bit_or(SymbolMath sym) throws Exception
    {
        throw new Exception();
    }

    public SymbolMath bit_eor(SymbolMath sym) throws Exception
    {
        throw new Exception();
    }

    public SymbolMath bit_not(SymbolMath sym) throws Exception
    {
        throw new Exception();
    }

    public SymbolMath negate(SymbolMath sym) throws Exception
    {
        throw new Exception();
    }

    public SymbolMath logical_and(SymbolMath sym) throws Exception
    {
        throw new Exception();
    }

    public SymbolMath logical_or(SymbolMath sym) throws Exception
    {
        throw new Exception();
    }

    public SymbolMath logical_eor(SymbolMath sym) throws Exception
    {
        throw new Exception();
    }

    public SymbolMath logical_not(SymbolMath sym) throws Exception
    {
        throw new Exception();
    }

    public SymbolMath logical_gt(SymbolMath sym) throws Exception
    {
        throw new Exception();
    }

    public SymbolMath logical_lt(SymbolMath sym) throws Exception
    {
        throw new Exception();
    }

    // TODO - logical eq/ne/ should work if both are labels.
    public SymbolMath logical_eq(SymbolMath sym) throws Exception
    {
        throw new Exception();
    }

    public SymbolMath logical_ne(SymbolMath sym) throws Exception
    {
        throw new Exception();
    }

    public SymbolMath logical_ge(SymbolMath sym) throws Exception
    {
        throw new Exception();
    }

    public SymbolMath logical_le(SymbolMath sym) throws Exception
    {
        throw new Exception();
    }

}
