package au.gov.ga.worldwind.panels.dataset;

import java.net.URL;

public interface IData extends IIconItem
{
	public String getName();
	public URL getInfoURL();
	public boolean isBase();
}
