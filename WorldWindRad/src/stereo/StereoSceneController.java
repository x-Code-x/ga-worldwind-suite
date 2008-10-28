package stereo;

import gov.nasa.worldwind.AbstractSceneController;
import gov.nasa.worldwind.View;
import gov.nasa.worldwind.render.DrawContext;

import java.nio.ByteBuffer;

import javax.media.opengl.GL;

import settings.Settings;
import settings.Settings.StereoMode;
import stereo.StereoOrbitView.Eye;

import com.sun.opengl.util.BufferUtil;

public class StereoSceneController extends AbstractSceneController
{
	private double lastVerticalExaggeration = -1;
	private boolean stereoTested = false;

	@Override
	protected void doRepaint(DrawContext dc)
	{
		Settings settings = Settings.get();
		double verticalExaggeration = settings.getVerticalExaggeration();
		if (lastVerticalExaggeration != verticalExaggeration)
		{
			setVerticalExaggeration(verticalExaggeration);
			lastVerticalExaggeration = verticalExaggeration;
		}

		GL gl = dc.getGL();
		if (!stereoTested)
		{
			ByteBuffer buffer16 = BufferUtil.newByteBuffer(16);
			gl.glGetBooleanv(GL.GL_STEREO, buffer16);
			settings.setStereoSupported(buffer16.get() == 1);
			stereoTested = true;
		}

		this.initializeFrame(dc);
		try
		{
			this.applyView(dc);
			this.createTerrain(dc);
			this.clearFrame(dc);
			this.pick(dc);
			this.clearFrame(dc);

			View view = dc.getView();
			StereoOrbitView stereo = null;
			if (view instanceof StereoOrbitView)
			{
				stereo = (StereoOrbitView) view;
			}
			if (stereo == null || !settings.isStereoEnabled())
			{
				this.draw(dc);
			}
			else
			{
				StereoMode mode = settings.getStereoMode();
				boolean swap = settings.isStereoSwap();

				stereo.setDrawing(true);
				stereo.setEye(swap ? Eye.RIGHT : Eye.LEFT);
				setupBuffer(gl, mode, Eye.LEFT);
				view.apply(dc);
				this.draw(dc);

				gl.glClear(GL.GL_DEPTH_BUFFER_BIT);
				gl.glDisable(GL.GL_FOG);

				stereo.setEye(swap ? Eye.LEFT : Eye.RIGHT);
				setupBuffer(gl, mode, Eye.RIGHT);
				view.apply(dc);
				this.draw(dc);

				stereo.setDrawing(false);
				restoreBuffer(gl, mode);
				view.apply(dc);
			}
		}
		finally
		{
			this.finalizeFrame(dc);
		}
	}

	private void setupBuffer(GL gl, StereoMode mode, Eye eye)
	{
		boolean left = eye == Eye.LEFT;
		switch (mode)
		{
			case RCANAGLYPH:
				gl.glColorMask(left, !left, !left, true);
				break;
			case GMANAGLYPH:
				gl.glColorMask(!left, left, !left, true);
				break;
			case BYANAGLYPH:
				gl.glColorMask(!left, !left, left, true);
				break;
			case STEREOBUFFER:
				gl.glDrawBuffer(GL.GL_BACK);
				break;
		}
	}

	private void restoreBuffer(GL gl, StereoMode mode)
	{
		switch (mode)
		{
			case BYANAGLYPH:
			case GMANAGLYPH:
			case RCANAGLYPH:
				gl.glColorMask(true, true, true, true);
				break;
			case STEREOBUFFER:
				gl.glDrawBuffer(GL.GL_BACK);
				break;
		}
	}
}
