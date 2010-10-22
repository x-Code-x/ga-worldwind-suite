package au.gov.ga.worldwind.common.layers.geometry.types;

import gov.nasa.worldwind.WorldWindow;
import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.avlist.AVList;
import gov.nasa.worldwind.avlist.AVListImpl;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.geom.Sector;
import gov.nasa.worldwind.layers.AbstractLayer;
import gov.nasa.worldwind.render.DrawContext;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import au.gov.ga.worldwind.common.layers.geometry.BasicStyleProviderImpl;
import au.gov.ga.worldwind.common.layers.geometry.GeometryLayer;
import au.gov.ga.worldwind.common.layers.geometry.Shape;
import au.gov.ga.worldwind.common.layers.geometry.ShapeProvider;
import au.gov.ga.worldwind.common.layers.geometry.StyleProvider;
import au.gov.ga.worldwind.common.layers.point.Attribute;
import au.gov.ga.worldwind.common.layers.point.Style;
import au.gov.ga.worldwind.common.util.AVKeyMore;

/**
 * A base class for {@link GeometryLayer} implementations.
 * <p/>
 * Provides convenience methods to aid in implementation.
 */
public abstract class GeometryLayerBase extends AbstractLayer implements GeometryLayer
{
	private AVList avList = new AVListImpl();
	private final URL shapeSourceUrl;
	private final String dataCacheName;
	private final ShapeProvider shapeProvider;
	private final StyleProvider styleProvider;
	
	@SuppressWarnings("unchecked")
	public GeometryLayerBase(AVList params)
	{
		try
		{
			URL shapeSourceContext = (URL) params.getValue(AVKeyMore.CONTEXT_URL);
			String url = params.getStringValue(AVKey.URL);
			shapeSourceUrl = new URL(shapeSourceContext, url);
		}
		catch (MalformedURLException e)
		{
			throw new IllegalArgumentException("Unable to parse shape source URL", e);
		}
		
		dataCacheName = params.getStringValue(AVKey.DATA_CACHE_NAME);
		
		shapeProvider = (ShapeProvider)params.getValue(AVKeyMore.SHAPE_PROVIDER);
		
		styleProvider = new BasicStyleProviderImpl((Collection<? extends Attribute>)params.getValue(AVKeyMore.SHAPE_ATTRIBUTES), 
												   (Collection<? extends Style>)params.getValue(AVKeyMore.SHAPE_STYLES)); 
		
		setValues(params);
	}
	
	@Override
	public Object setValue(String key, Object value)
	{
		return avList.setValue(key, value);
	}

	@Override
	public AVList setValues(AVList avList)
	{
		return avList.setValues(avList);
	}

	@Override
	public Object getValue(String key)
	{
		return avList.getValue(key);
	}

	@Override
	public Collection<Object> getValues()
	{
		return avList.getValues();
	}

	@Override
	public String getStringValue(String key)
	{
		return avList.getStringValue(key);
	}

	@Override
	public Set<Entry<String, Object>> getEntries()
	{
		return avList.getEntries();
	}

	@Override
	public boolean hasKey(String key)
	{
		return avList.hasKey(key);
	}

	@Override
	public Object removeKey(String key)
	{
		return avList.removeKey(key);
	}

	@Override
	public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener)
	{
		avList.addPropertyChangeListener(propertyName, listener);
	}

	@Override
	public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener)
	{
		avList.removePropertyChangeListener(propertyName, listener);
	}

	@Override
	public void addPropertyChangeListener(PropertyChangeListener listener)
	{
		avList.addPropertyChangeListener(listener);
	}

	@Override
	public void removePropertyChangeListener(PropertyChangeListener listener)
	{
		avList.removePropertyChangeListener(listener);
	}

	@Override
	public void firePropertyChange(String propertyName, Object oldValue, Object newValue)
	{
		avList.firePropertyChange(propertyName, oldValue, newValue);
	}

	@Override
	public void firePropertyChange(PropertyChangeEvent propertyChangeEvent)
	{
		avList.firePropertyChange(propertyChangeEvent);
	}

	@Override
	public AVList copy()
	{
		return avList.copy();
	}

	@Override
	public AVList clearList()
	{
		return avList.clearList();
	}

	@Override
	public Sector getSector()
	{
		// TODO: Cache the calculated sector
		List<Position> points = new ArrayList<Position>();
		for (Shape shape : getShapes())
		{
			points.addAll(shape.getPoints());
		}
		return Sector.boundingSector(points);
	}

	@Override
	public void setup(WorldWindow wwd)
	{
		// Subclasses may override to perform required setup
	} 
	
	@Override
	public void loadComplete()
	{
		// Subclasses may override to perform required post-load processing
	}
	
	@Override
	public URL getShapeSourceUrl() throws MalformedURLException
	{
		return shapeSourceUrl;
	}
	
	@Override
	public String getDataCacheName()
	{
		return dataCacheName;
	}
	
	@Override
	protected final void doRender(DrawContext dc)
	{
		if (isEnabled())
		{
			getShapeProvider().requestShapes(this);
		}
		renderGeometry(dc);
	}
	
	protected ShapeProvider getShapeProvider()
	{
		return shapeProvider;
	}
	
	protected StyleProvider getStyleProvider()
	{
		return styleProvider;
	}
}