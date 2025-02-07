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
package au.gov.ga.worldwind.common.util.exaggeration;

import gov.nasa.worldwind.render.DrawContext;

/**
 * The default implementation of the {@link VerticalExaggerationService} that simply uses the global
 * vertical exaggeration as provided by the supplied {@link DrawContext}.
 * 
 * @author James Navin (james.navin@ga.gov.au)
 */
public class DefaultVerticalExaggerationServiceImpl implements VerticalExaggerationService
{

	@Override
	public double applyVerticalExaggeration(DrawContext dc, double elevation)
	{
		return dc.getVerticalExaggeration() * elevation;
	}

	@Override
	public double getGlobalVerticalExaggeration(DrawContext dc)
	{
		return dc.getVerticalExaggeration();
	}

}
