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
package au.gov.ga.worldwind.common.layers.curtain;

import gov.nasa.worldwind.geom.Vec4;
import gov.nasa.worldwind.render.DrawContext;
import gov.nasa.worldwind.render.Renderable;
import gov.nasa.worldwind.util.OGLStackHandler;

import java.nio.DoubleBuffer;

import javax.media.opengl.GL;

/**
 * A {@link Renderable} piece of geometry that draws a segment (or section) of a
 * curtain/path for the {@link TiledCurtainLayer}.
 * 
 * @author Michael de Hoog (michael.dehoog@ga.gov.au)
 */
public class SegmentGeometry implements Renderable
{
	private final Vec4 referenceCenter;
	private final DoubleBuffer vertices;
	private final DoubleBuffer texCoords;

	public SegmentGeometry(DoubleBuffer vertices, DoubleBuffer texCoords, Vec4 referenceCenter)
	{
		this.vertices = vertices;
		this.texCoords = texCoords;
		this.referenceCenter = referenceCenter;
	}

	@Override
	public void render(DrawContext dc)
	{
		render(dc, 1);
	}

	public void render(DrawContext dc, int numTextureUnits)
	{
		dc.getView().pushReferenceCenter(dc, referenceCenter);

		GL gl = dc.getGL();
		OGLStackHandler ogsh = new OGLStackHandler();

		try
		{
			ogsh.pushClientAttrib(gl, GL.GL_CLIENT_VERTEX_ARRAY_BIT);

			gl.glEnableClientState(GL.GL_VERTEX_ARRAY);

			/*if (dc.getGLRuntimeCapabilities().isUseVertexBufferObject())
			{
				//Use VBO's
				gl.glBindBuffer(GL.GL_ARRAY_BUFFER, tile.ri.bufferIdVertices);
				gl.glVertexPointer(3, GL.GL_DOUBLE, 0, 0);

				for (int i = 0; i < numTextureUnits; i++)
				{
					gl.glClientActiveTexture(GL.GL_TEXTURE0 + i);
					gl.glEnableClientState(GL.GL_TEXTURE_COORD_ARRAY);

					gl.glBindBuffer(GL.GL_ARRAY_BUFFER, tile.ri.bufferIdTexCoords);
					gl.glTexCoordPointer(2, GL.GL_DOUBLE, 0, 0);
				}

				gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, tile.ri.bufferIdIndicies);

				gl.glDrawElements(javax.media.opengl.GL.GL_TRIANGLE_STRIP, tile.ri.indices.limit(),
						javax.media.opengl.GL.GL_UNSIGNED_INT, 0);
			}
			else*/
			{
				//Use Vertex Arrays
				gl.glVertexPointer(3, GL.GL_DOUBLE, 0, vertices.rewind());

				for (int i = 0; i < numTextureUnits; i++)
				{
					gl.glClientActiveTexture(GL.GL_TEXTURE0 + i);
					gl.glEnableClientState(GL.GL_TEXTURE_COORD_ARRAY);
					gl.glTexCoordPointer(2, GL.GL_DOUBLE, 0, texCoords.rewind());
				}

				int vertexCount = vertices.limit() / 3;
				gl.glDrawArrays(GL.GL_TRIANGLE_STRIP, 0, vertexCount);
			}
		}
		finally
		{
			ogsh.pop(gl);
		}
		dc.getView().popReferenceCenter(dc);
	}

	public Vec4 getReferenceCenter()
	{
		return referenceCenter;
	}

	public DoubleBuffer getVertices()
	{
		return vertices;
	}

	public DoubleBuffer getTexCoords()
	{
		return texCoords;
	}
}
