import java.util.HashMap;
/*
 * Created on Feb 2, 2006
 *
 */

/**
 * @author Kelvin
 *
 */
public class SymbolMap extends HashMap<String, Symbol> {

    private static final long serialVersionUID = 1L;


    /*
     * returns false if it's a duplicate symbol.
     */
    public boolean AddEntry(String name, Symbol sym)
	{
	    if (this.containsKey(name))
	        return false;
	    this.put(name, sym);
	    return true;
	}
	
	public Symbol FindEntry(String name)
	{
	    return this.get(name);
	}
}
