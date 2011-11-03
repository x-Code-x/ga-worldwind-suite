/*
 * iModel.java
 *
 * Created on February 27, 2008, 9:55 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package gov.nasa.worldwind.formats.models;

import gov.nasa.worldwind.formats.models.geometry.Model;

/**
 *
 * @author RodgersGB
 */
public interface iModel3DRenderer {
    public void render(Object context, Model model);
    public void debug(boolean value);
}
