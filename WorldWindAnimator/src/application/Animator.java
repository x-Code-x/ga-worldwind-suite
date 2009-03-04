package application;

import gov.nasa.worldwind.BasicModel;
import gov.nasa.worldwind.Configuration;
import gov.nasa.worldwind.Model;
import gov.nasa.worldwind.View;
import gov.nasa.worldwind.WorldWind;
import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.awt.WorldWindowGLCanvas;
import gov.nasa.worldwind.event.RenderingEvent;
import gov.nasa.worldwind.event.RenderingListener;
import gov.nasa.worldwind.geom.Angle;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.layers.CompassLayer;
import gov.nasa.worldwind.layers.LayerList;
import gov.nasa.worldwind.layers.SkyGradientLayer;
import gov.nasa.worldwind.layers.StarsLayer;
import gov.nasa.worldwind.layers.WorldMapLayer;
import gov.nasa.worldwind.layers.Earth.BMNGWMSLayer;
import gov.nasa.worldwind.layers.Earth.LandsatI3WMSLayer;
import gov.nasa.worldwind.util.StatusBar;
import gov.nasa.worldwind.view.OrbitView;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import layers.WestMacALOS;
import path.SimpleAnimation;
import tessellator.ConfigurableTessellator;
import util.ChangeFrameListener;
import util.FrameSlider;
import view.BasicRollOrbitView;

public class Animator
{
	static
	{
		if (Configuration.isMacOS())
		{
			System.setProperty("apple.laf.useScreenMenuBar", "true");
			System.setProperty(
					"com.apple.mrj.application.apple.menu.about.name",
					"World Wind Application");
			System.setProperty("com.apple.mrj.application.growbox.intrudes",
					"false");
			System.setProperty("apple.awt.brushMetalLook", "true");
		}

		try
		{
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		}
		catch (Exception e)
		{
		}

		System.setProperty("http.proxyHost", "proxy.agso.gov.au");
		System.setProperty("http.proxyPort", "8080");
		System.setProperty("http.nonProxyHosts", "localhost");
	}

	public static void main(String[] args)
	{
		new Animator();
	}

	private JFrame frame;
	private WorldWindowGLCanvas wwd;
	private boolean takingScreenshot = false;
	private FrameSlider slider;
	private SimpleAnimation animation = null;
	private File file = null;
	private boolean autokey = false;
	private boolean applying = false;
	private Updater updater;
	private boolean changed = false;
	private boolean stop = false;
	private boolean settingSlider = false;
	private ChangeListener animationChangeListener;

	public Animator()
	{
		Configuration.setValue(AVKey.LAYERS_CLASS_NAMES, "");
		Configuration.setValue(AVKey.VIEW_CLASS_NAME, BasicRollOrbitView.class
				.getName());
		Configuration.setValue(AVKey.TESSELLATOR_CLASS_NAME,
				ConfigurableTessellator.class.getName());
		/*Configuration.setValue(AVKey.GLOBE_CLASS_NAME, WestMacGlobe.class
				.getName());*/
		Configuration.setValue(AVKey.INPUT_HANDLER_CLASS_NAME,
				AWTInputHandler.class.getName());

		animationChangeListener = new ChangeListener()
		{
			public void stateChanged(ChangeEvent e)
			{
				changed = true;
				setTitleBar();
			}
		};

		animation = new SimpleAnimation();
		resetChanged();
		updater = new Updater();

		frame = new JFrame();
		frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		frame.addWindowListener(new WindowAdapter()
		{
			@Override
			public void windowClosing(WindowEvent e)
			{
				quit();
			}
		});

		frame.setLayout(new BorderLayout());
		wwd = new WorldWindowGLCanvas();
		Model model = new BasicModel();
		/*((ConfigurableTessellator) model.getGlobe().getTessellator())
				.setLog10ResolutionTarget(2.2);*/
		wwd.setModel(model);
		wwd.setPreferredSize(new Dimension(1024, 576));
		frame.add(wwd, BorderLayout.CENTER);

		LayerList layers = model.getLayers();

		layers.add(new StarsLayer());
		layers.add(new SkyGradientLayer());
		//layers.add(new FogLayer());
		//layers.add(new BMNGOneImage());
		//layers.add(new BMNGWMSLayer());
		//layers.add(new LandsatI3WMSLayer());
		//layers.add(new EarthNASAPlaceNameLayer());
		//layers.add(new CompassLayer());
		//layers.add(new WorldMapLayer());
		//layers.add(new ScalebarLayer());
		//layers.add(new MGRSGraticuleLayer());
		//layers.add(new TernaryAreasLayer());
		layers.add(new WestMacALOS());

		//wwd.getSceneController().setVerticalExaggeration(5.0);

		JPanel bottom = new JPanel(new BorderLayout());
		frame.add(bottom, BorderLayout.SOUTH);

		slider = new FrameSlider(0, 0, animation.getFrameCount());
		slider.setMinimumSize(new Dimension(0, 54));
		bottom.add(slider, BorderLayout.CENTER);
		slider.addChangeListener(new ChangeListener()
		{
			public void stateChanged(ChangeEvent e)
			{
				if (!settingSlider)
				{
					if (animation.size() > 0)
					{
						applyView();
						wwd.redraw();
					}
					stop = true;
				}
			}
		});
		slider.addChangeFrameListener(new ChangeFrameListener()
		{
			public void frameChanged(int index, int oldFrame, int newFrame)
			{
				animation.setFrame(index, newFrame);
				updateSlider();
				applyView();
				wwd.redraw();
			}
		});

		getView().addPropertyChangeListener(AVKey.VIEW,
				new PropertyChangeListener()
				{
					public void propertyChange(PropertyChangeEvent evt)
					{
						if (autokey && !applying)
						{
							addFrame();
						}
					}
				});

		StatusBar statusBar = new StatusBar();
		statusBar.setBorder(BorderFactory.createLoweredBevelBorder());
		bottom.add(statusBar, BorderLayout.SOUTH);
		statusBar.setEventSource(wwd);

		ToolTipManager.sharedInstance().setLightWeightPopupEnabled(false);
		JPopupMenu.setDefaultLightWeightPopupEnabled(false);
		createMenuBar();

		setTitleBar();
		updateSlider();

		frame.pack();
		frame.setVisible(true);
	}

	private void createMenuBar()
	{
		JMenuBar menuBar = new JMenuBar();
		frame.setJMenuBar(menuBar);

		JMenu menu;
		JMenuItem menuItem;

		menu = new JMenu("File");
		menuBar.add(menu);

		menuItem = new JMenuItem("New");
		menu.add(menuItem);
		menuItem.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				newFile();
			}
		});

		menuItem = new JMenuItem("Open...");
		menu.add(menuItem);
		menuItem.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				open();
			}
		});

		menuItem = new JMenuItem("Save");
		menu.add(menuItem);
		menuItem.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				save();
			}
		});

		menuItem = new JMenuItem("Save As...");
		menu.add(menuItem);
		menuItem.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				saveAs();
			}
		});

		menu.addSeparator();

		menuItem = new JMenuItem("Exit");
		menu.add(menuItem);
		menuItem.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				quit();
			}
		});


		menu = new JMenu("Frame");
		menuBar.add(menu);

		menuItem = new JMenuItem("Add key");
		menu.add(menuItem);
		menuItem.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				addFrame();
			}
		});

		menuItem = new JMenuItem("Remove key");
		menu.add(menuItem);
		menuItem.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				int index = animation.indexOf(slider.getValue());
				if (index >= 0)
				{
					animation.removeFrame(index);
				}
				updateSlider();
			}
		});

		menu.addSeparator();

		final JCheckBoxMenuItem autoKeyItem = new JCheckBoxMenuItem("Auto key",
				autokey);
		menu.add(autoKeyItem);
		autoKeyItem.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				autokey = autoKeyItem.isSelected();
			}
		});

		menuItem = new JMenuItem("Set frame count...");
		menu.add(menuItem);
		menuItem.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				int frames = slider.getLength() - 1;
				Object value = JOptionPane.showInputDialog(frame,
						"Number of frames:", "Set frame count",
						JOptionPane.QUESTION_MESSAGE, null, null, frames);
				try
				{
					frames = Integer.parseInt((String) value);
				}
				catch (Exception ex)
				{
				}
				animation.setFrameCount(frames);
				updateSlider();
			}
		});

		menuItem = new JMenuItem("Smooth eye speed");
		menu.add(menuItem);
		menuItem.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				if (JOptionPane
						.showConfirmDialog(
								frame,
								"This will redistribute keyframes to attempt to smooth the eye speed.\nDo you wish to continue?",
								"Smooth eye speed", JOptionPane.YES_NO_OPTION,
								JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION)
				{
					animation.smoothEyeSpeed();
					updateSlider();
				}
			}
		});


		menu = new JMenu("Animation");
		menuBar.add(menu);

		menuItem = new JMenuItem("Preview");
		menu.add(menuItem);
		menuItem.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				animate(false);
			}
		});

		menuItem = new JMenuItem("Render...");
		menu.add(menuItem);
		menuItem.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				animate(true);
			}
		});
	}

	private OrbitView getView()
	{
		return (OrbitView) wwd.getView();
	}

	private void addFrame()
	{
		OrbitView view = getView();
		if (view.getPitch().equals(Angle.ZERO))
		{
			view.setPitch(Angle.fromDegrees(0.1));
			wwd.redrawNow();
		}
		updater.addFrame(slider.getValue(), view);
		//animation.addFrame(slider.getValue(), view);
		//updateAnimation();
	}

	private void applyView()
	{
		int frame = slider.getValue();
		if (animation.getFirstFrame() <= frame
				&& frame <= animation.getLastFrame())
		{
			applying = true;
			animation.applyFrame(getView(), frame);
			applying = false;
		}
	}

	public void quit()
	{
		if (querySave())
		{
			frame.dispose();
			System.exit(0);
		}
	}

	private void newFile()
	{
		if (querySave())
		{
			animation = new SimpleAnimation();
			resetChanged();
			setFile(null);
			updateSlider();
		}
	}

	private void open()
	{
		if (querySave())
		{
			JFileChooser chooser = new JFileChooser();
			if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION)
			{
				File newFile = chooser.getSelectedFile();
				SimpleAnimation newAnimation = new SimpleAnimation();
				try
				{
					newAnimation.load(newFile);
					animation = newAnimation;
					resetChanged();
					setFile(newFile);
				}
				catch (Exception e)
				{
					JOptionPane.showMessageDialog(frame, "Could not open '"
							+ newFile.getAbsolutePath() + "'.\n" + e, "Error",
							JOptionPane.ERROR_MESSAGE);
				}
			}
			updateSlider();
		}
	}

	private void save()
	{
		if (file == null)
		{
			saveAs();
		}
		else
		{
			save(file);
		}
	}

	private void saveAs()
	{
		JFileChooser chooser = new JFileChooser();
		if (chooser.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION)
		{
			setFile(chooser.getSelectedFile());
			if (file != null)
				save(file);
		}
	}

	private void save(File file)
	{
		if (file != null)
		{
			try
			{
				animation.save(file);
				resetChanged();
			}
			catch (IOException e)
			{
				JOptionPane.showMessageDialog(frame, "Saving failed.\n" + e,
						"Error", JOptionPane.ERROR_MESSAGE);
			}
			setTitleBar();
		}
	}

	private void setFile(File file)
	{
		this.file = file;
		setTitleBar();
	}

	private void resetChanged()
	{
		animation.removeChangeListener(animationChangeListener);
		animation.addChangeListener(animationChangeListener);
		changed = false;
	}

	private boolean querySave()
	{
		if (!changed)
			return true;
		String file = this.file == null ? "Animation" : "'"
				+ this.file.getName() + "'";
		String message = file + " has been modified. Save changes?";
		int value = JOptionPane.showConfirmDialog(frame, message, "Save",
				JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
		if (value == JOptionPane.CANCEL_OPTION)
			return false;
		if (value == JOptionPane.YES_OPTION)
			save();
		return true;
	}

	private void setTitleBar()
	{
		String file;
		String title = "World Wind Animator";
		if (this.file != null)
			file = this.file.getName();
		else
			file = "New animation";
		if (changed)
			file += " *";
		title = file + " - " + title;
		frame.setTitle(title);
	}

	private void updateSlider()
	{
		slider.clearKeys();
		for (int i = 0; i < animation.size(); i++)
		{
			slider.addKey(animation.getFrame(i));
		}
		slider.setMin(0);
		slider.setMax(animation.getFrameCount());
	}

	private void setSlider(int frame)
	{
		settingSlider = true;
		slider.setValue(frame);
		settingSlider = false;
	}

	private void animate(final boolean savingFrames)
	{
		if (animation != null && animation.size() > 0)
		{
			Thread thread = new Thread(new Runnable()
			{
				public void run()
				{
					stop = false;
					Animator.this.frame.setAlwaysOnTop(savingFrames);

					if (savingFrames)
					{
						Toolkit tk = Toolkit.getDefaultToolkit();
						BufferedImage image = new BufferedImage(1, 1,
								BufferedImage.TYPE_INT_ARGB);
						Cursor blankCursor = tk.createCustomCursor(image,
								new Point(0, 0), "BlackCursor");
						wwd.setCursor(blankCursor);
					}

					View view = getView();
					boolean detectCollisions = view.isDetectCollisions();
					view.setDetectCollisions(false);

					int firstFrame = Math.max(slider.getValue(), animation
							.getFirstFrame());
					int lastFrame = animation.getLastFrame();
					for (int frame = firstFrame; frame <= lastFrame; frame++)
					{
						setSlider(frame);
						applyView();
						wwd.redrawNow();

						if (stop)
							break;

						if (savingFrames)
						{
							takeScreenshot("frames/frame" + frame + ".tga");
						}
					}

					view.setDetectCollisions(detectCollisions);

					Animator.this.frame.setAlwaysOnTop(false);
					wwd.setCursor(null);
				}
			});
			thread.start();
		}
	}

	private void takeScreenshot(String filename)
	{
		if (!EventQueue.isDispatchThread())
		{
			takingScreenshot = true;
		}

		wwd.addRenderingListener(new Screenshotter(filename));
		wwd.redraw();

		if (!EventQueue.isDispatchThread())
		{
			while (takingScreenshot)
			{
				try
				{
					Thread.sleep(100);
				}
				catch (InterruptedException e)
				{
					e.printStackTrace();
				}
			}
		}
	}

	private class Screenshotter implements RenderingListener
	{
		private final String filename;

		public Screenshotter(String filename)
		{
			this.filename = filename;
		}

		public void stageChanged(RenderingEvent event)
		{
			if (WorldWind.getTaskService().hasActiveTasks()
					|| WorldWind.getRetrievalService().hasActiveTasks())
			{
				sleep();
				wwd.redraw();
			}
			else if (event.getStage() == RenderingEvent.BEFORE_BUFFER_SWAP)
			{
				wwd.removeRenderingListener(this);

				File out = new File(filename);
				if (!out.getParentFile().exists())
				{
					out.getParentFile().mkdirs();
				}
				try
				{
					com.sun.opengl.util.Screenshot.writeToTargaFile(out, wwd
							.getWidth(), wwd.getHeight());
				}
				catch (Exception e)
				{
					e.printStackTrace();
				}

				takingScreenshot = false;
			}
		}

		private void sleep()
		{
			try
			{
				Thread.sleep(100);
			}
			catch (InterruptedException e)
			{
				e.printStackTrace();
			}
		}
	};

	private class Updater
	{
		private Map<Integer, ViewParameters> toApply = new HashMap<Integer, ViewParameters>();
		private Object waiter = new Object();

		public Updater()
		{
			Thread thread = new Thread()
			{
				@Override
				public void run()
				{
					while (true)
					{
						Integer key = getNextKey();
						if (key != null)
						{
							ViewParameters vp = removeValue(key);
							animation.addFrame(key, vp.eye, vp.center);
							updateSlider();
						}
						else
						{
							synchronized (waiter)
							{
								try
								{
									waiter.wait();
								}
								catch (InterruptedException e)
								{
									e.printStackTrace();
								}
							}
						}
					}
				}
			};
			thread.setDaemon(true);
			thread.start();
		}

		private synchronized Integer getNextKey()
		{
			Set<Integer> keyset = toApply.keySet();
			if (keyset.isEmpty())
				return null;
			return keyset.iterator().next();
		}

		private synchronized ViewParameters removeValue(Integer key)
		{
			return toApply.remove(key);
		}

		public synchronized void addFrame(int frame, OrbitView view)
		{
			toApply.put(frame, ViewParameters.fromView(view));
			synchronized (waiter)
			{
				waiter.notify();
			}
		}

	}

	private static class ViewParameters
	{
		public Position eye;
		public Position center;

		public static ViewParameters fromView(OrbitView view)
		{
			ViewParameters vp = new ViewParameters();
			vp.eye = view.getEyePosition();
			vp.center = view.getCenterPosition();
			return vp;
		}
	}
}
