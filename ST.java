/*
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

import omf.*;
*/
/*
 * Created on Dec 29, 2005
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */

/**
 * @author Kelvin
 *
 * TODO To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Style - Code Templates
 */

/*
public class ST {
	private ArrayList fData;
	public ST()
	{
		fData = new ArrayList();
	}
	public boolean Define(OMF_Label lab, OMF_Expr value, int level)
	{
		HashMap m = null;
		while (fData.size() < (level + 1))
			fData.add(null);
		m = (HashMap)fData.get(level);
		if (m == null)
		{
			m = new HashMap();
			fData.add(level, m);
		}
		// should throw... oh well.
		if (m.containsKey(lab))
			return false;
		m.put(lab, value);
		return true;
	}
	
	// evalute expressions.
	void Evaluate()
	{
		HashMap global = (HashMap)fData.get(0);
		if (global == null) global = new HashMap();
		
		for (int i = 0; i < fData.size(); i++)
		{
			HashMap m = (HashMap)fData.get(i);
			if (m == null) continue;

			HashMap tmp = (HashMap) global.clone();
			if (i != 0)
				tmp.putAll(m);						
			
			// ok, iterate through the hashmap
			Set set = m.keySet();
			Iterator iter = set.iterator();
			while(iter.hasNext())
			{

				OMF_Expr e = (OMF_Expr)m.get(iter.next());
				e.Evaluate(tmp);
				
			}
		}
		
		
	}
}

*/