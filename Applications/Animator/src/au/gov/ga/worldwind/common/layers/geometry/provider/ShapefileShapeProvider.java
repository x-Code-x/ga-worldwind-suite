package au.gov.ga.worldwind.common.layers.geometry.provider;

import gov.nasa.worldwind.formats.shapefile.DBaseRecord;
import gov.nasa.worldwind.formats.shapefile.Shapefile;
import gov.nasa.worldwind.formats.shapefile.ShapefileRecord;
import gov.nasa.worldwind.formats.shapefile.ShapefileUtils;
import gov.nasa.worldwind.geom.Sector;
import gov.nasa.worldwind.util.Logging;
import gov.nasa.worldwind.util.VecBuffer;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import au.gov.ga.worldwind.common.layers.geometry.BasicShapeImpl;
import au.gov.ga.worldwind.common.layers.geometry.GeometryLayer;
import au.gov.ga.worldwind.common.layers.geometry.Shape;
import au.gov.ga.worldwind.common.layers.geometry.Shape.Type;
import au.gov.ga.worldwind.common.layers.geometry.ShapeProvider;
import au.gov.ga.worldwind.common.util.Util;

/**
 * A {@link ShapeProvider} that loads shapes from a zipped Shapefile.
 * <p/>
 * A new shape is defined for each record in the shapefile.
 */
public class ShapefileShapeProvider extends ShapeProviderBase implements ShapeProvider
{
	private static Map<String, Type> shapeTypeMap = new HashMap<String, Type>();
	static
	{
		shapeTypeMap.put(Shapefile.SHAPE_POINT, Type.POINT);
		shapeTypeMap.put(Shapefile.SHAPE_POINT_M, Type.POINT);
		shapeTypeMap.put(Shapefile.SHAPE_POINT_Z, Type.POINT);
		
		shapeTypeMap.put(Shapefile.SHAPE_POLYLINE, Type.LINE);
		shapeTypeMap.put(Shapefile.SHAPE_POLYLINE_M, Type.LINE);
		shapeTypeMap.put(Shapefile.SHAPE_POLYLINE_Z, Type.LINE);
		
		shapeTypeMap.put(Shapefile.SHAPE_POLYGON, Type.POLYGON);
		shapeTypeMap.put(Shapefile.SHAPE_POLYGON_M, Type.POLYGON);
		shapeTypeMap.put(Shapefile.SHAPE_POLYGON_Z, Type.POLYGON);
	}
	
	private Sector sector;
	
	@Override
	protected boolean doLoadShapes(URL url, GeometryLayer layer)
	{
		try
		{
			Shapefile shapefile = ShapefileUtils.openZippedShapefile(Util.urlToFile(url));
			while (shapefile.hasNext())
			{
				ShapefileRecord record = shapefile.nextRecord();
				DBaseRecord values = record.getAttributes();

				Shape loadedShape = new BasicShapeImpl(url.getPath() + record.getRecordNumber(), getShapeTypeFromRecord(record)); 
				for (int part = 0; part < record.getNumberOfParts(); part++)
				{
					VecBuffer buffer = record.getPointBuffer(part);
					int size = buffer.getSize();
					for (int i = 0; i < size; i++)
					{
						loadedShape.addPoint(buffer.getPosition(i), values);
					}
				}
				
				layer.addShape(loadedShape);
			}

			sector = Sector.fromDegrees(shapefile.getBoundingRectangle());
			layer.loadComplete();
		}
		catch (Exception e)
		{
			String message = "Error loading points";
			Logging.logger().log(Level.SEVERE, message, e);
			return false;
		}
		return true;
	}

	@Override
	public Sector getSector()
	{
		return sector;
	}

	private static Type getShapeTypeFromRecord(ShapefileRecord record)
	{
		return shapeTypeMap.get(record.getShapeType());
	}
	
}