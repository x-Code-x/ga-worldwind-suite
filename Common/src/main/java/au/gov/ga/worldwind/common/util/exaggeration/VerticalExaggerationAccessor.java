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
 * A static accessor used to apply the correct vertical exaggeration values to
 * a given elevation value
 * 
 * @author James Navin (james.navin@ga.gov.au)
 */
public class VerticalExaggerationAccessor
{
	private static VerticalExaggerationService service = new DefaultVerticalExaggerationServiceImpl();
	
	public static VerticalExaggerationService getService()
	{
		if (VerticalExaggerationAccessor.service == null)
		{
			service = new DefaultVerticalExaggerationServiceImpl();
		}
		return service;
	}
	
	public static void setService(VerticalExaggerationService service)
	{
		VerticalExaggerationAccessor.service = service;
	}
	
	/** @see VerticalExaggerationService#applyVerticalExaggeration(DrawContext, double) */
	public static double applyVerticalExaggeration(DrawContext dc, double elevation)
	{
		return getService().applyVerticalExaggeration(dc, elevation);
	}
	
	/** @see VerticalExaggerationService#getGlobalVerticalExaggeration(DrawContext) */
	public static double getGlobalVerticalExaggeration(DrawContext dc)
	{
		return getService().getGlobalVerticalExaggeration(dc);
	}
	
	private VerticalExaggerationAccessor(){}
}
