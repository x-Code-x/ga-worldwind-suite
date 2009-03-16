package application;

import gov.nasa.worldwind.Configuration;
import gov.nasa.worldwind.Model;
import gov.nasa.worldwind.SceneController;
import gov.nasa.worldwind.View;
import gov.nasa.worldwind.WorldWind;
import gov.nasa.worldwind.WorldWindow;
import gov.nasa.worldwind.WorldWindowGLDrawable;
import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.avlist.AVList;
import gov.nasa.worldwind.cache.BasicTextureCache;
import gov.nasa.worldwind.cache.TextureCache;
import gov.nasa.worldwind.event.InputHandler;
import gov.nasa.worldwind.event.NoOpInputHandler;
import gov.nasa.worldwind.event.PositionListener;
import gov.nasa.worldwind.event.RenderingExceptionListener;
import gov.nasa.worldwind.event.RenderingListener;
import gov.nasa.worldwind.event.SelectListener;
import gov.nasa.worldwind.exception.WWRuntimeException;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.pick.PickedObjectList;
import gov.nasa.worldwind.util.Logging;
import gov.nasa.worldwind.util.PerformanceStatistic;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import javax.media.opengl.GLCapabilities;
import javax.media.opengl.GLDrawableFactory;
import javax.media.opengl.GLPbuffer;

public class WorldWindowPBuffer implements WorldWindow, PropertyChangeListener
{
	private static final GLCapabilities caps = new GLCapabilities();

	static
	{
		caps.setAlphaBits(8);
		caps.setRedBits(8);
		caps.setGreenBits(8);
		caps.setBlueBits(8);
		caps.setDepthBits(24);
		caps.setDoubleBuffered(false);
	}

	private final WorldWindowGLDrawable wwd; // WorldWindow interface delegates to wwd
	private GLPbuffer pbuffer;

	public WorldWindowPBuffer(Model model, int width, int height)
	{
		GLDrawableFactory fac = GLDrawableFactory.getFactory();
		GLPbuffer buf = fac.createGLPbuffer(caps, null, width, height, null);
		pbuffer = new PBufferDelegate(buf)
		{
			private boolean awaitingDisplay;

			@Override
			public void repaint()
			{
				if (!awaitingDisplay)
				{
					awaitingDisplay = true;
					super.repaint();
				}
			}

			@Override
			public void display()
			{
				awaitingDisplay = true;
				super.display();
				awaitingDisplay = false;
			}
		};

		try
		{
			this.wwd = ((WorldWindowGLDrawable) WorldWind
					.createConfigurationComponent(AVKey.WORLD_WINDOW_CLASS_NAME));
			setModel(model);
			this.wwd.initDrawable(pbuffer);
			this.wwd.initTextureCache(createTextureCache());
			this.createView();
			this.createDefaultInputHandler();
			WorldWind.addPropertyChangeListener(WorldWind.SHUTDOWN_EVENT, this);
		}
		catch (Exception e)
		{
			String message = Logging
					.getMessage("Awt.WorldWindowGLSurface.UnabletoCreateWindow");
			Logging.logger().severe(message);
			throw new WWRuntimeException(message, e);
		}
	}

	private static final long FALLBACK_TEXTURE_CACHE_SIZE = 60000000;

	private static TextureCache createTextureCache()
	{
		long cacheSize = Configuration.getLongValue(AVKey.TEXTURE_CACHE_SIZE,
				FALLBACK_TEXTURE_CACHE_SIZE);
		return new BasicTextureCache((long) (0.8 * cacheSize), cacheSize);
	}

	public void propertyChange(PropertyChangeEvent evt)
	{
		//noinspection StringEquality
		if (evt.getPropertyName() == WorldWind.SHUTDOWN_EVENT)
			this.shutdown();
	}

	public void shutdown()
	{
		WorldWind.removePropertyChangeListener(WorldWind.SHUTDOWN_EVENT, this);
		this.wwd.shutdown();
	}

	public GLPbuffer getPbuffer()
	{
		return pbuffer;
	}

	private void createView()
	{
		this.setView((View) WorldWind
				.createConfigurationComponent(AVKey.VIEW_CLASS_NAME));
	}

	private void createDefaultInputHandler()
	{
		/*this.setInputHandler((InputHandler) WorldWind
				.createConfigurationComponent(AVKey.INPUT_HANDLER_CLASS_NAME));*/
		this.setInputHandler(null);
	}

	public InputHandler getInputHandler()
	{
		return this.wwd.getInputHandler();
	}

	public void setInputHandler(InputHandler inputHandler)
	{
		if (this.wwd.getInputHandler() != null)
			this.wwd.getInputHandler().setEventSource(null); // remove this window as a source of events

		this.wwd.setInputHandler(inputHandler != null ? inputHandler
				: new NoOpInputHandler());
		if (inputHandler != null)
			inputHandler.setEventSource(this);
	}

	public SceneController getSceneController()
	{
		return this.wwd.getSceneController();
	}

	public TextureCache getTextureCache()
	{
		return this.wwd.getTextureCache();
	}

	public void redraw()
	{
		redrawNow();
	}

	public void redrawNow()
	{
		this.wwd.redrawNow();
	}

	public void setModel(Model model)
	{
		// null models are permissible
		this.wwd.setModel(model);
	}

	public Model getModel()
	{
		return this.wwd.getModel();
	}

	public void setView(View view)
	{
		// null views are permissible
		if (view != null)
			this.wwd.setView(view);
	}

	public View getView()
	{
		return this.wwd.getView();
	}

	public void setModelAndView(Model model, View view)
	{ // null models/views are permissible
		this.setModel(model);
		this.setView(view);
	}

	public void addRenderingListener(RenderingListener listener)
	{
		this.wwd.addRenderingListener(listener);
	}

	public void removeRenderingListener(RenderingListener listener)
	{
		this.wwd.removeRenderingListener(listener);
	}

	public void addSelectListener(SelectListener listener)
	{
		this.wwd.getInputHandler().addSelectListener(listener);
		this.wwd.addSelectListener(listener);
	}

	public void removeSelectListener(SelectListener listener)
	{
		this.wwd.getInputHandler().removeSelectListener(listener);
		this.wwd.removeSelectListener(listener);
	}

	public void addPositionListener(PositionListener listener)
	{
		this.wwd.addPositionListener(listener);
	}

	public void removePositionListener(PositionListener listener)
	{
		this.wwd.removePositionListener(listener);
	}

	public void addRenderingExceptionListener(
			RenderingExceptionListener listener)
	{
		this.wwd.addRenderingExceptionListener(listener);
	}

	public void removeRenderingExceptionListener(
			RenderingExceptionListener listener)
	{
		this.wwd.removeRenderingExceptionListener(listener);
	}

	public Position getCurrentPosition()
	{
		return this.wwd.getCurrentPosition();
	}

	public PickedObjectList getObjectsAtCurrentPosition()
	{
		return this.wwd.getSceneController() != null ? this.wwd
				.getSceneController().getPickedObjectList() : null;
	}

	public void setValue(String key, Object value)
	{
		this.wwd.setValue(key, value);
	}

	public void setValues(AVList avList)
	{
		this.wwd.setValues(avList);
	}

	public Object getValue(String key)
	{
		return this.wwd.getValue(key);
	}

	public Collection<Object> getValues()
	{
		return this.wwd.getValues();
	}

	public Set<Map.Entry<String, Object>> getEntries()
	{
		return this.wwd.getEntries();
	}

	public String getStringValue(String key)
	{
		return this.wwd.getStringValue(key);
	}

	public boolean hasKey(String key)
	{
		return this.wwd.hasKey(key);
	}

	public void removeKey(String key)
	{
		this.wwd.removeKey(key);
	}

	public synchronized void addPropertyChangeListener(
			PropertyChangeListener listener)
	{
		this.wwd.addPropertyChangeListener(listener);
	}

	public synchronized void addPropertyChangeListener(String propertyName,
			PropertyChangeListener listener)
	{
		this.wwd.addPropertyChangeListener(propertyName, listener);
	}

	public synchronized void removePropertyChangeListener(
			PropertyChangeListener listener)
	{
		this.wwd.removePropertyChangeListener(listener);
	}

	public synchronized void removePropertyChangeListener(String propertyName,
			PropertyChangeListener listener)
	{
		this.wwd.removePropertyChangeListener(listener);
	}

	public void firePropertyChange(String propertyName, Object oldValue,
			Object newValue)
	{
		if (this.wwd != null)
			this.wwd.firePropertyChange(propertyName, oldValue, newValue);
	}

	public void firePropertyChange(PropertyChangeEvent propertyChangeEvent)
	{
		this.wwd.firePropertyChange(propertyChangeEvent);
	}

	public AVList copy()
	{
		return this.wwd.copy();
	}

	public AVList clearList()
	{
		return this.wwd.clearList();
	}

	public void setPerFrameStatisticsKeys(Set<String> keys)
	{
		this.wwd.setPerFrameStatisticsKeys(keys);
	}

	public Collection<PerformanceStatistic> getPerFrameStatistics()
	{
		return this.wwd.getPerFrameStatistics();
	}
}
