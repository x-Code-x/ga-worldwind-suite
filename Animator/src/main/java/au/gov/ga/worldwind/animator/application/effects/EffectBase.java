/*******************************************************************************
 * Copyright 2012 Geoscience Australia
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package au.gov.ga.worldwind.animator.application.effects;

import gov.nasa.worldwind.render.DrawContext;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.media.opengl.GL;

import au.gov.ga.worldwind.animator.animation.AnimatableBase;
import au.gov.ga.worldwind.animator.animation.Animation;
import au.gov.ga.worldwind.animator.animation.parameter.Parameter;
import au.gov.ga.worldwind.animator.application.render.FrameBuffer;
import au.gov.ga.worldwind.common.util.Validate;

/**
 * Abstract base implementation of the {@link Effect} interface. Most
 * {@link Effect} implementations should use this as their base class.
 * 
 * @author Michael de Hoog (michael.dehoog@ga.gov.au)
 */
public abstract class EffectBase extends AnimatableBase implements Effect
{
	/**
	 * The animatable parameters used by this effect.
	 */
	protected final List<Parameter> parameters = new ArrayList<Parameter>();

	/**
	 * The frame buffer to draw to for this effect.
	 */
	protected final FrameBuffer frameBuffer = new FrameBuffer();

	public EffectBase(String name, Animation animation)
	{
		super(name, animation);
	}

	protected EffectBase()
	{
		super();
		setName(getDefaultName());
	}

	@Override
	public Collection<Parameter> getParameters()
	{
		return Collections.unmodifiableCollection(parameters);
	}

	@Override
	public void addParameter(EffectParameter parameter)
	{
		if (parameter == null)
		{
			return;
		}
		Validate.isTrue(this.equals(parameter.getEffect()), "Parameter is not linked to the correct layer. Expected '"
				+ this + "'.");
		parameters.add(parameter);

		parameter.addChangeListener(this);
	}

	@Override
	protected void doApply()
	{
		for (Parameter parameter : parameters)
		{
			Validate.isTrue(parameter instanceof EffectParameter, "Incorrect Parameter type"); //should never occur
			((EffectParameter) parameter).apply();
		}
	}

	@Override
	public final void bindFrameBuffer(DrawContext dc, Dimension dimensions)
	{
		GL gl = dc.getGL();

		//this will create the framebuffer if it doesn't exist
		frameBuffer.resize(gl, dimensions, true);
		resizeExtraFrameBuffers(dc, dimensions);
		frameBuffer.bind(gl);
	}

	/**
	 * If this effect requires any extra frame buffers, resize them here.
	 * 
	 * @param dc
	 *            Draw context
	 * @param dimensions
	 *            Render dimensions
	 */
	protected void resizeExtraFrameBuffers(DrawContext dc, Dimension dimensions)
	{
	}

	@Override
	public final void unbindFrameBuffer(DrawContext dc, Dimension dimensions)
	{
		frameBuffer.unbind(dc.getGL());
	}

	@Override
	public final void drawFrameBufferWithEffect(DrawContext dc, Dimension dimensions)
	{
		drawFrameBufferWithEffect(dc, dimensions, frameBuffer);
	}

	@Override
	public final void releaseResources(DrawContext dc)
	{
		if (frameBuffer.isCreated())
		{
			frameBuffer.delete(dc.getGL());
		}
		releaseEffect(dc);
	}

	/**
	 * Called after the scene is rendered, and possibly after another
	 * {@link Effect}'s frame buffer has been bound. The effect should render
	 * it's framebuffer using it's effect shader here.
	 * 
	 * @param dc
	 *            Draw context
	 * @param dimensions
	 *            Dimensions of the viewport (includes render scale during
	 *            rendering)
	 * @param frameBuffer
	 *            {@link FrameBuffer} containing the scene to apply the effect
	 *            to.
	 */
	protected abstract void drawFrameBufferWithEffect(DrawContext dc, Dimension dimensions, FrameBuffer frameBuffer);

	/**
	 * Release any resources associated with this effect. This is called every
	 * frame if the effect is disabled.
	 * 
	 * @param dc
	 *            Draw context
	 */
	protected abstract void releaseEffect(DrawContext dc);
}
