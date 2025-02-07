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
package au.gov.ga.worldwind.common.ui;

import java.lang.reflect.InvocationTargetException;

import javax.swing.SwingUtilities;

/**
 * Utilities for working with Swing GUIs
 * 
 * @author James Navin (james.navin@ga.gov.au)
 */
public class SwingUtil
{
	/**
	 * Invokes the provided runnable task on the EDT. Use to ensure GUI updates
	 * are performed on the EDT, where they should be.
	 * 
	 * @throws SwingEDTException
	 *             if an exception occurs while executing the provided task on
	 *             the EDT. This exception is unchecked and contains the
	 *             original cause.
	 */
	public static void invokeTaskOnEDT(Runnable task) throws SwingEDTException
	{
		try
		{
			if (SwingUtilities.isEventDispatchThread())
			{
				task.run();
			}
			else
			{
				SwingUtilities.invokeAndWait(task);
			}
		}
		catch (InvocationTargetException e)
		{
			throw new SwingEDTException(e.getCause());
		}
		catch (InterruptedException e)
		{
			e.printStackTrace();
		}
	}

	/**
	 * Invokes the provided runnable task on the EDT. This method doesn't wait
	 * for the task to complete (unless the calling thread is the EDT, in which
	 * case the task is run immediately). Use to ensure GUI updates are
	 * performed on the EDT, where they should be.
	 */
	public static void invokeLaterTaskOnEDT(Runnable task)
	{
		if (SwingUtilities.isEventDispatchThread())
		{
			task.run();
		}
		else
		{
			SwingUtilities.invokeLater(task);
		}
	}
}
