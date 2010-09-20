package au.gov.ga.worldwind.common.layers.curtain;

import gov.nasa.worldwind.View;
import gov.nasa.worldwind.WorldWind;
import gov.nasa.worldwind.geom.Extent;
import gov.nasa.worldwind.geom.LatLon;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.geom.Sector;
import gov.nasa.worldwind.geom.Vec4;
import gov.nasa.worldwind.layers.AbstractLayer;
import gov.nasa.worldwind.render.DrawContext;
import gov.nasa.worldwind.render.Renderable;
import gov.nasa.worldwind.util.Logging;
import gov.nasa.worldwind.util.OGLTextRenderer;
import gov.nasa.worldwind.util.PerformanceStatistic;
import gov.nasa.worldwind.util.WWIO;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.PriorityBlockingQueue;

import javax.imageio.ImageIO;
import javax.media.opengl.GL;

import com.sun.opengl.util.j2d.TextRenderer;

public abstract class TiledCurtainLayer extends AbstractLayer
{
	//TODO initialise these!
	private Path path;
	private double top = 100;
	private double bottom = -100000;
	private boolean followTerrain = true;

	// Infrastructure
	private static final LevelComparer levelComparer = new LevelComparer();
	private final CurtainLevelSet levels;
	private List<CurtainTextureTile> topLevels;
	private boolean forceLevelZeroLoads = false;
	private boolean levelZeroLoaded = false;
	private boolean retainLevelZeroTiles = false;
	private String tileCountName;
	private double splitScale = 0.9;
	private boolean useMipMaps = true;
	private boolean useTransparentTextures = false;
	private List<String> supportedImageFormats = new ArrayList<String>();
	private String textureFormat;

	// Diagnostic flags
	private boolean showImageTileOutlines = false;
	private boolean drawTileBoundaries = false;
	private boolean drawTileIDs = false;
	private boolean drawBoundingVolumes = false;

	// Stuff computed each frame
	private List<CurtainTextureTile> currentTiles = new ArrayList<CurtainTextureTile>();
	private CurtainTextureTile currentResourceTile;
	private boolean atMaxResolution = false;
	private PriorityBlockingQueue<Runnable> requestQ = new PriorityBlockingQueue<Runnable>(200);

	abstract protected void requestTexture(DrawContext dc, CurtainTile tile);

	abstract protected void forceTextureLoad(CurtainTile tile);

	public TiledCurtainLayer(CurtainLevelSet levelSet)
	{
		if (levelSet == null)
		{
			String message = Logging.getMessage("nullValue.LevelSetIsNull");
			Logging.logger().severe(message);
			throw new IllegalArgumentException(message);
		}

		this.levels = new CurtainLevelSet(levelSet); // the caller's levelSet may change internally, so we copy it.

		//        this.createTopLevelTiles();

		this.setPickEnabled(false); // textures are assumed to be terrain unless specifically indicated otherwise.
		this.tileCountName = this.getName() + " Tiles";
	}

	@Override
	public Object setValue(String key, Object value)
	{
		// Offer it to the level set
		if (this.getLevels() != null)
			this.getLevels().setValue(key, value);

		return super.setValue(key, value);
	}

	@Override
	public Object getValue(String key)
	{
		Object value = super.getValue(key);

		return value != null ? value : this.getLevels().getValue(key); // see if the level set has it
	}

	@Override
	public void setName(String name)
	{
		super.setName(name);
		this.tileCountName = this.getName() + " Tiles";
	}

	public boolean isForceLevelZeroLoads()
	{
		return this.forceLevelZeroLoads;
	}

	public void setForceLevelZeroLoads(boolean forceLevelZeroLoads)
	{
		this.forceLevelZeroLoads = forceLevelZeroLoads;
	}

	public boolean isRetainLevelZeroTiles()
	{
		return retainLevelZeroTiles;
	}

	public void setRetainLevelZeroTiles(boolean retainLevelZeroTiles)
	{
		this.retainLevelZeroTiles = retainLevelZeroTiles;
	}

	public boolean isDrawTileIDs()
	{
		return drawTileIDs;
	}

	public void setDrawTileIDs(boolean drawTileIDs)
	{
		this.drawTileIDs = drawTileIDs;
	}

	public boolean isDrawTileBoundaries()
	{
		return drawTileBoundaries;
	}

	public void setDrawTileBoundaries(boolean drawTileBoundaries)
	{
		this.drawTileBoundaries = drawTileBoundaries;
	}

	public boolean isShowImageTileOutlines()
	{
		return showImageTileOutlines;
	}

	public void setShowImageTileOutlines(boolean showImageTileOutlines)
	{
		this.showImageTileOutlines = showImageTileOutlines;
	}

	public boolean isDrawBoundingVolumes()
	{
		return drawBoundingVolumes;
	}

	public void setDrawBoundingVolumes(boolean drawBoundingVolumes)
	{
		this.drawBoundingVolumes = drawBoundingVolumes;
	}

	/**
	 * Sets the parameter controlling a layer's displayed resolution as distance
	 * changes between the globe's surface and the eye point. Higher resolution
	 * is displayed as the split scale increases from 1.0. Lower resolution is
	 * displayed as the split scale decreases from 1.0. The default value is
	 * specified in the layer's configuration, or is 0.9 if not specified there.
	 * 
	 * @param splitScale
	 *            a value near 1.0 that controls the image resolution as the
	 *            distance between the globe's surface and the eye point change.
	 *            Increasing values select higher resolution, decreasing values
	 *            select lower resolution. Typical values range between 0.8 and
	 *            1.2.
	 */
	public void setSplitScale(double splitScale)
	{
		this.splitScale = splitScale;
	}

	/**
	 * Returns the split scale value controlling image resolution relative to
	 * the distance between the globe's surface at the image position and the
	 * eye point.
	 * 
	 * @return the current split scale.
	 * 
	 * @see #setSplitScale(double)
	 */
	public double getSplitScale()
	{
		return this.splitScale;
	}

	protected CurtainLevelSet getLevels()
	{
		return levels;
	}

	protected PriorityBlockingQueue<Runnable> getRequestQ()
	{
		return requestQ;
	}

	@Override
	public boolean isMultiResolution()
	{
		return this.getLevels() != null && this.getLevels().getNumLevels() > 1;
	}

	@Override
	public boolean isAtMaxResolution()
	{
		return this.atMaxResolution;
	}

	/**
	 * Returns the format used to store images in texture memory, or null if
	 * images are stored in their native format.
	 * 
	 * @return the texture image format; null if images are stored in their
	 *         native format.
	 * 
	 * @see {@link #setTextureFormat(String)}
	 */
	public String getTextureFormat()
	{
		return this.textureFormat;
	}

	/**
	 * Specifies the format used to store images in texture memory, or null to
	 * store images in their native format. Suppported texture formats are as
	 * follows:
	 * <ul>
	 * <li><code>image/dds</code> - Stores images in the compressed DDS format.
	 * If the image is already in DDS format it's stored as-is.</li>
	 * </ul>
	 * 
	 * @param textureFormat
	 *            the texture image format; null to store images in their native
	 *            format.
	 */
	public void setTextureFormat(String textureFormat)
	{
		this.textureFormat = textureFormat;
	}

	public boolean isUseMipMaps()
	{
		return useMipMaps;
	}

	public void setUseMipMaps(boolean useMipMaps)
	{
		this.useMipMaps = useMipMaps;
	}

	public boolean isUseTransparentTextures()
	{
		return this.useTransparentTextures;
	}

	public void setUseTransparentTextures(boolean useTransparentTextures)
	{
		this.useTransparentTextures = useTransparentTextures;
	}

	/**
	 * Specifies the time of the layer's most recent dataset update, beyond
	 * which cached data is invalid. If greater than zero, the layer ignores and
	 * eliminates any in-memory or on-disk cached data older than the time
	 * specified, and requests new information from the data source. If zero,
	 * the default, the layer applies any expiry times associated with its
	 * individual levels, but only for on-disk cached data. In-memory cached
	 * data is expired only when the expiry time is specified with this method
	 * and is greater than zero. This method also overwrites the expiry times of
	 * the layer's individual levels if the value specified to the method is
	 * greater than zero.
	 * 
	 * @param expiryTime
	 *            the expiry time of any cached data, expressed as a number of
	 *            milliseconds beyond the epoch. The default expiry time is
	 *            zero.
	 * 
	 * @see System#currentTimeMillis() for a description of milliseconds beyond
	 *      the epoch.
	 */
	@Override
	public void setExpiryTime(long expiryTime) // Override this method to use intrinsic level-specific expiry times
	{
		super.setExpiryTime(expiryTime);

		if (expiryTime > 0)
			this.levels.setExpiryTime(expiryTime); // remove this in sub-class to use level-specific expiry times
	}

	public List<CurtainTextureTile> getTopLevels()
	{
		if (this.topLevels == null)
			this.createTopLevelTiles();

		return topLevels;
	}

	private void createTopLevelTiles()
	{
		CurtainLevel level = levels.getFirstLevel();
		int rowCount = level.getRowCount();
		int colCount = level.getColumnCount();

		this.topLevels = new ArrayList<CurtainTextureTile>(rowCount * colCount);

		for (int row = 0; row < rowCount; row++)
		{
			for (int col = 0; col < colCount; col++)
			{
				Segment segment = level.computeSegmentForRowColumn(row, col);
				CurtainTextureTile tile = new CurtainTextureTile(level, segment, row, col);
				this.topLevels.add(tile);
			}
		}
	}

	private void loadAllTopLevelTextures(DrawContext dc)
	{
		for (CurtainTextureTile tile : this.getTopLevels())
		{
			if (!tile.isTextureInMemory(dc.getTextureCache()))
				this.forceTextureLoad(tile);
		}

		this.levelZeroLoaded = true;
	}

	// ============== Tile Assembly ======================= //
	// ============== Tile Assembly ======================= //
	// ============== Tile Assembly ======================= //

	private void assembleTiles(DrawContext dc)
	{
		this.currentTiles.clear();

		for (CurtainTextureTile tile : this.getTopLevels())
		{
			//TODO to tell if a certain CurtainTile is visible, it must first be mapped onto it's
			//piece of geometry. Perhaps there should be a geometry piece cache, and geometry pieces
			//are created/cached for each CurtainTile. Then all view geometry calculations can be made
			//on the geometry piece instead of the CurtainTile.

			if (this.isTileVisible(dc, tile))
			{
				this.currentResourceTile = null;
				this.addTileOrDescendants(dc, tile);
			}
		}
	}

	private void addTileOrDescendants(DrawContext dc, CurtainTextureTile tile)
	{
		if (this.meetsRenderCriteria(dc, tile))
		{
			this.addTile(dc, tile);
			return;
		}

		// The incoming tile does not meet the rendering criteria, so it must be subdivided and those
		// subdivisions tested against the criteria.

		// All tiles that meet the selection criteria are drawn, but some of those tiles will not have
		// textures associated with them either because their texture isn't loaded yet or because they
		// are finer grain than the layer has textures for. In these cases the tiles use the texture of
		// the closest ancestor that has a texture loaded. This ancestor is called the currentResourceTile.
		// A texture transform is applied during rendering to align the sector's texture coordinates with the
		// appropriate region of the ancestor's texture.

		CurtainTextureTile ancestorResource = null;

		try
		{
			// TODO: Revise this to reflect that the parent layer is only requested while the algorithm continues
			// to search for the layer matching the criteria.
			// At this point the tile does not meet the render criteria but it may have its texture in memory.
			// If so, register this tile as the resource tile. If not, then this tile will be the next level
			// below a tile with texture in memory. So to provide progressive resolution increase, add this tile
			// to the draw list. That will cause the tile to be drawn using its parent tile's texture, and it will
			// cause it's texture to be requested. At some future call to this method the tile's texture will be in
			// memory, it will not meet the render criteria, but will serve as the parent to a tile that goes
			// through this same process as this method recurses. The result of all this is that a tile isn't rendered
			// with its own texture unless all its parents have their textures loaded. In addition to causing
			// progressive resolution increase, this ensures that the parents are available as the user zooms out, and
			// therefore the layer remains visible until the user is zoomed out to the point the layer is no longer
			// active.
			if (tile.isTextureInMemory(dc.getTextureCache()) || tile.getLevelNumber() == 0)
			{
				ancestorResource = this.currentResourceTile;
				this.currentResourceTile = tile;
			}
			else if (!tile.getLevel().isEmpty())
			{
				//                this.addTile(dc, tile);
				//                return;

				// Issue a request for the parent before descending to the children.
				//                if (tile.getLevelNumber() < this.levels.getNumLevels())
				//                {
				//                    // Request only tiles with data associated at this level
				//                    if (!this.levels.isResourceAbsent(tile))
				//                        this.requestTexture(dc, tile);
				//                }
			}

			CurtainTextureTile[] subTiles =
					tile.createSubTiles(this.levels.getLevel(tile.getLevelNumber() + 1));
			for (CurtainTextureTile child : subTiles)
			{
				if (this.isTileVisible(dc, child))
					this.addTileOrDescendants(dc, child);
			}
		}
		finally
		{
			if (ancestorResource != null) // Pop this tile as the currentResource ancestor
				this.currentResourceTile = ancestorResource;
		}
	}

	private void addTile(DrawContext dc, CurtainTextureTile tile)
	{
		tile.setFallbackTile(null);

		if (tile.isTextureInMemory(dc.getTextureCache()))
		{
			this.addTileToCurrent(tile);
			return;
		}

		// Level 0 loads may be forced
		if (tile.getLevelNumber() == 0 && this.forceLevelZeroLoads
				&& !tile.isTextureInMemory(dc.getTextureCache()))
		{
			this.forceTextureLoad(tile);
			if (tile.isTextureInMemory(dc.getTextureCache()))
			{
				this.addTileToCurrent(tile);
				return;
			}
		}

		// Tile's texture isn't available, so request it
		if (tile.getLevelNumber() < this.levels.getNumLevels())
		{
			// Request only tiles with data associated at this level
			if (!this.levels.isResourceAbsent(tile))
				this.requestTexture(dc, tile);
		}

		// Set up to use the currentResource tile's texture
		if (this.currentResourceTile != null)
		{
			if (this.currentResourceTile.getLevelNumber() == 0 && this.forceLevelZeroLoads
					&& !this.currentResourceTile.isTextureInMemory(dc.getTextureCache())
					&& !this.currentResourceTile.isTextureInMemory(dc.getTextureCache()))
				this.forceTextureLoad(this.currentResourceTile);

			if (this.currentResourceTile.isTextureInMemory(dc.getTextureCache()))
			{
				tile.setFallbackTile(currentResourceTile);
				this.addTileToCurrent(tile);
			}
		}
	}

	private void addTileToCurrent(CurtainTextureTile tile)
	{
		this.currentTiles.add(tile);
	}

	private boolean isTileVisible(DrawContext dc, CurtainTextureTile tile)
	{
		Segment segment = tile.getSegment();
		LatLon start = path.getPercentLatLon(segment.getStart());
		LatLon end = path.getPercentLatLon(segment.getEnd());
		Extent extent = path.getSegmentExtent(dc, segment, top, bottom);

		return extent.intersects(dc.getView().getFrustumInModelCoordinates())
				&& (dc.getVisibleSector() == null || dc.getVisibleSector().intersectsSegment(start,
						end));
	}

	private boolean meetsRenderCriteria(DrawContext dc, CurtainTextureTile tile)
	{
		return this.levels.isFinalLevel(tile.getLevelNumber())
				|| !needToSplit(dc, tile.getSegment());
	}

	private boolean needToSplit(DrawContext dc, Segment segment)
	{
		Vec4[] points = path.getPointsInSegment(dc, segment, top, bottom);
		Vec4 centerPoint = path.getCenterPoint(dc, segment.getCenter(), top, bottom);

		View view = dc.getView();
		double minDistance = view.getEyePoint().distanceTo3(centerPoint);
		for (Vec4 point : points)
		{
			minDistance = Math.min(minDistance, view.getEyePoint().distanceTo3(point));
		}

		double segmentLength = path.getSegmentLengthInRadians(segment);
		double cellSize = (Math.PI * segmentLength * dc.getGlobe().getRadius()) / 20; // TODO

		return !(Math.log10(cellSize) <= (Math.log10(minDistance) - this.getSplitScale()));
	}

	//	private boolean atMaxLevel(DrawContext dc)
	//	{
	//		Position vpc = dc.getViewportCenterPosition();
	//		if (dc.getView() == null || this.getLevels() == null || vpc == null)
	//			return false;
	//
	//		if (!this.getLevels().getSector().contains(vpc.getLatitude(), vpc.getLongitude()))
	//			return true;
	//
	//		CurtainLevel nextToLast = this.getLevels().getNextToLastLevel();
	//		if (nextToLast == null)
	//			return true;
	//
	//		Sector centerSector =
	//				nextToLast.computeSectorForPosition(vpc.getLatitude(), vpc.getLongitude(),
	//						this.levels.getTileOrigin());
	//		return this.needToSplit(dc, centerSector);
	//	}

	// ============== Rendering ======================= //
	// ============== Rendering ======================= //
	// ============== Rendering ======================= //

	@Override
	public void render(DrawContext dc)
	{
		//this.atMaxResolution = this.atMaxLevel(dc); //TODO calculate
		super.render(dc);
	}

	@Override
	protected final void doRender(DrawContext dc)
	{
		if (this.forceLevelZeroLoads && !this.levelZeroLoaded)
			this.loadAllTopLevelTextures(dc);
		if (dc.getSurfaceGeometry() == null || dc.getSurfaceGeometry().size() < 1)
			return;

		dc.getGeographicSurfaceTileRenderer().setShowImageTileOutlines(this.showImageTileOutlines);

		draw(dc);
	}

	private void draw(DrawContext dc)
	{
		this.assembleTiles(dc); // Determine the tiles to draw.

		if (this.currentTiles.size() >= 1)
		{
			if (this.getScreenCredit() != null)
			{
				dc.addScreenCredit(this.getScreenCredit());
			}

			CurtainTextureTile[] sortedTiles = new CurtainTextureTile[this.currentTiles.size()];
			sortedTiles = this.currentTiles.toArray(sortedTiles);
			Arrays.sort(sortedTiles, levelComparer);

			GL gl = dc.getGL();

			if (this.isUseTransparentTextures() || this.getOpacity() < 1)
			{
				gl.glPushAttrib(GL.GL_COLOR_BUFFER_BIT | GL.GL_POLYGON_BIT | GL.GL_CURRENT_BIT);
				this.setBlendingFunction(dc);
			}
			else
			{
				gl.glPushAttrib(GL.GL_COLOR_BUFFER_BIT | GL.GL_POLYGON_BIT);
			}

			gl.glPolygonMode(GL.GL_FRONT, GL.GL_FILL);
			gl.glEnable(GL.GL_CULL_FACE);
			gl.glCullFace(GL.GL_BACK);

			dc.setPerFrameStatistic(PerformanceStatistic.IMAGE_TILE_COUNT, this.tileCountName,
					this.currentTiles.size());
			//dc.getGeographicSurfaceTileRenderer().renderTiles(dc, this.currentTiles);
			//TODO

			gl.glPopAttrib();

			if (this.drawTileIDs)
				this.drawTileIDs(dc, this.currentTiles);

			if (this.drawBoundingVolumes)
				this.drawBoundingVolumes(dc, this.currentTiles);

			// Check texture expiration. Memory-cached textures are checked for expiration only when an explicit,
			// non-zero expiry time has been set for the layer. If none has been set, the expiry times of the layer's
			// individual levels are used, but only for images in the local file cache, not textures in memory. This is
			// to avoid incurring the overhead of checking expiration of in-memory textures, a very rarely used feature.
			if (this.getExpiryTime() > 0 && this.getExpiryTime() < System.currentTimeMillis())
				this.checkTextureExpiration(dc, this.currentTiles);

			this.currentTiles.clear();
		}

		this.sendRequests();
		this.requestQ.clear();
	}

	private void checkTextureExpiration(DrawContext dc, List<CurtainTextureTile> tiles)
	{
		for (CurtainTextureTile tile : tiles)
		{
			if (tile.isTextureExpired())
				this.requestTexture(dc, tile);
		}
	}

	protected void setBlendingFunction(DrawContext dc)
	{
		// Set up a premultiplied-alpha blending function. Any texture read by JOGL will have alpha-premultiplied color
		// components, as will any DDS file created by World Wind or the World Wind WMS. We'll also set up the base
		// color as a premultiplied color, so that any incoming premultiplied color will be properly combined with the
		// base color.

		GL gl = dc.getGL();

		double alpha = this.getOpacity();
		gl.glColor4d(alpha, alpha, alpha, alpha);
		gl.glEnable(GL.GL_BLEND);
		gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE_MINUS_SRC_ALPHA);
	}

	private void sendRequests()
	{
		Runnable task = this.requestQ.poll();
		while (task != null)
		{
			if (!WorldWind.getTaskService().isFull())
			{
				WorldWind.getTaskService().addTask(task);
			}
			task = this.requestQ.poll();
		}
	}

	@Override
	public boolean isLayerInView(DrawContext dc)
	{
		if (dc == null)
		{
			String message = Logging.getMessage("nullValue.DrawContextIsNull");
			Logging.logger().severe(message);
			throw new IllegalStateException(message);
		}

		if (dc.getView() == null)
		{
			String message =
					Logging.getMessage("layers.AbstractLayer.NoViewSpecifiedInDrawingContext");
			Logging.logger().severe(message);
			throw new IllegalStateException(message);
		}

		return dc.getVisibleSector() == null
				|| dc.getVisibleSector().intersectsSegment(path.getPercentLatLon(0d),
						path.getPercentLatLon(1d));
	}

	private Vec4 computeReferencePoint(DrawContext dc)
	{
		if (dc.getViewportCenterPosition() != null)
			return dc.getGlobe().computePointFromPosition(dc.getViewportCenterPosition());

		java.awt.geom.Rectangle2D viewport = dc.getView().getViewport();
		int x = (int) viewport.getWidth() / 2;
		for (int y = (int) (0.5 * viewport.getHeight()); y >= 0; y--)
		{
			Position pos = dc.getView().computePositionFromScreenPoint(x, y);
			if (pos == null)
				continue;

			return dc.getGlobe()
					.computePointFromPosition(pos.getLatitude(), pos.getLongitude(), 0d);
		}

		return null;
	}

	protected Vec4 getReferencePoint(DrawContext dc)
	{
		return this.computeReferencePoint(dc);
	}

	private static class LevelComparer implements Comparator<CurtainTextureTile>
	{
		@Override
		public int compare(CurtainTextureTile ta, CurtainTextureTile tb)
		{
			int la =
					ta.getFallbackTile() == null ? ta.getLevelNumber() : ta.getFallbackTile()
							.getLevelNumber();
			int lb =
					tb.getFallbackTile() == null ? tb.getLevelNumber() : tb.getFallbackTile()
							.getLevelNumber();

			return la < lb ? -1 : la == lb ? 0 : 1;
		}
	}

	private void drawTileIDs(DrawContext dc, List<CurtainTextureTile> tiles)
	{
		java.awt.Rectangle viewport = dc.getView().getViewport();
		TextRenderer textRenderer =
				OGLTextRenderer.getOrCreateTextRenderer(dc.getTextRendererCache(),
						java.awt.Font.decode("Arial-Plain-13"));

		dc.getGL().glDisable(GL.GL_DEPTH_TEST);
		dc.getGL().glDisable(GL.GL_BLEND);
		dc.getGL().glDisable(GL.GL_TEXTURE_2D);

		textRenderer.beginRendering(viewport.width, viewport.height);
		textRenderer.setColor(java.awt.Color.YELLOW);
		for (CurtainTextureTile tile : tiles)
		{
			String tileLabel = tile.getLabel();

			if (tile.getFallbackTile() != null)
				tileLabel += "/" + tile.getFallbackTile().getLabel();

			Vec4 pt = path.getCenterPoint(dc, tile.getSegment().getCenter(), top, bottom);
			pt = dc.getView().project(pt);
			textRenderer.draw(tileLabel, (int) pt.x, (int) pt.y);
		}
		textRenderer.setColor(java.awt.Color.WHITE);
		textRenderer.endRendering();
	}

	private void drawBoundingVolumes(DrawContext dc, List<CurtainTextureTile> tiles)
	{
		float[] previousColor = new float[4];
		dc.getGL().glGetFloatv(GL.GL_CURRENT_COLOR, previousColor, 0);
		dc.getGL().glColor3d(0, 1, 0);

		for (CurtainTextureTile tile : tiles)
		{
			Extent extent = path.getSegmentExtent(dc, tile.getSegment(), top, bottom);
			if (extent instanceof Renderable)
				((Renderable) extent).render(dc);
		}

		Segment segment = new Segment(0, 1, 0, 1);
		Extent extent = path.getSegmentExtent(dc, segment, top, bottom);
		if (extent instanceof Renderable)
		{
			dc.getGL().glColor3d(1, 1, 0);
			((Renderable) extent).render(dc);
		}

		dc.getGL().glColor4fv(previousColor, 0);
	}

	//**************************************************************//
	//********************  Configuration  *************************//
	//**************************************************************//

	//	/**
	//	 * Creates a configuration document for a TiledImageLayer described by the
	//	 * specified params. The returned document may be used as a construction
	//	 * parameter to {@link gov.nasa.worldwind.layers.BasicTiledImageLayer}.
	//	 * 
	//	 * @param params
	//	 *            parameters describing the TiledImageLayer.
	//	 * 
	//	 * @return a configuration document for the TiledImageLayer.
	//	 */
	//	public static Document createTiledImageLayerConfigDocument(AVList params)
	//	{
	//		Document doc = WWXML.createDocumentBuilder(true).newDocument();
	//
	//		Element root = WWXML.setDocumentElement(doc, "Layer");
	//		WWXML.setIntegerAttribute(root, "version", 1);
	//		WWXML.setTextAttribute(root, "layerType", "TiledImageLayer");
	//
	//		createTiledImageLayerConfigElements(params, root);
	//
	//		return doc;
	//	}
	//
	//	/**
	//	 * Appends TiledImageLayer configuration parameters as elements to the
	//	 * specified context. This appends elements for the following parameters:
	//	 * <table>
	//	 * <tr>
	//	 * <th>Parameter</th>
	//	 * <th>Element Path</th>
	//	 * <th>Type</th>
	//	 * </tr>
	//	 * <tr>
	//	 * <td>{@link AVKey#SERVICE_NAME}</td>
	//	 * <td>Service/@serviceName</td>
	//	 * <td>String</td>
	//	 * </tr>
	//	 * <tr>
	//	 * <td>{@link AVKey#IMAGE_FORMAT}</td>
	//	 * <td>ImageFormat</td>
	//	 * <td>String</td>
	//	 * </tr>
	//	 * <tr>
	//	 * <td>{@link AVKey#AVAILABLE_IMAGE_FORMATS}</td>
	//	 * <td>AvailableImageFormats/ImageFormat</td>
	//	 * <td>String array</td>
	//	 * </tr>
	//	 * <tr>
	//	 * <td>{@link AVKey#FORCE_LEVEL_ZERO_LOADS}</td>
	//	 * <td>ForceLevelZeroLoads</td>
	//	 * <td>Boolean</td>
	//	 * </tr>
	//	 * <tr>
	//	 * <td>{@link AVKey#RETAIN_LEVEL_ZERO_TILES}</td>
	//	 * <td>RetainLevelZeroTiles</td>
	//	 * <td>Boolean</td>
	//	 * </tr>
	//	 * <tr>
	//	 * <td>{@link AVKey#TEXTURE_FORMAT}</td>
	//	 * <td>TextureFormat</td>
	//	 * <td>String</td>
	//	 * </tr>
	//	 * <tr>
	//	 * <td>{@link AVKey#USE_MIP_MAPS}</td>
	//	 * <td>UseMipMaps</td>
	//	 * <td>Boolean</td>
	//	 * </tr>
	//	 * <tr>
	//	 * <td>{@link AVKey#USE_TRANSPARENT_TEXTURES}</td>
	//	 * <td>UseTransparentTextures</td>
	//	 * <td>Boolean</td>
	//	 * </tr>
	//	 * <tr>
	//	 * <td>{@link AVKey#URL_CONNECT_TIMEOUT}</td>
	//	 * <td>RetrievalTimeouts/ConnectTimeout/Time</td>
	//	 * <td>Integer milliseconds</td>
	//	 * </tr>
	//	 * <tr>
	//	 * <td>{@link AVKey#URL_READ_TIMEOUT}</td>
	//	 * <td>RetrievalTimeouts/ReadTimeout/Time</td>
	//	 * <td>Integer milliseconds</td>
	//	 * </tr>
	//	 * <tr>
	//	 * <td>{@link AVKey#RETRIEVAL_QUEUE_STALE_REQUEST_LIMIT}</td>
	//	 * <td>RetrievalTimeouts/StaleRequestLimit/Time</td>
	//	 * <td>Integer milliseconds</td>
	//	 * </tr>
	//	 * </table>
	//	 * This also writes common layer and LevelSet configuration parameters by
	//	 * invoking
	//	 * {@link gov.nasa.worldwind.layers.AbstractLayer#createLayerConfigElements(gov.nasa.worldwind.avlist.AVList, org.w3c.dom.Element)}
	//	 * and
	//	 * {@link DataConfigurationUtils#createLevelSetConfigElements(gov.nasa.worldwind.avlist.AVList, org.w3c.dom.Element)}
	//	 * .
	//	 * 
	//	 * @param params
	//	 *            the key-value pairs which define the TiledImageLayer
	//	 *            configuration parameters.
	//	 * @param context
	//	 *            the XML document root on which to append TiledImageLayer
	//	 *            configuration elements.
	//	 * 
	//	 * @return a reference to context.
	//	 * 
	//	 * @throws IllegalArgumentException
	//	 *             if either the parameters or the context are null.
	//	 */
	//	public static Element createTiledImageLayerConfigElements(AVList params, Element context)
	//	{
	//		if (params == null)
	//		{
	//			String message = Logging.getMessage("nullValue.ParametersIsNull");
	//			Logging.logger().severe(message);
	//			throw new IllegalArgumentException(message);
	//		}
	//
	//		if (context == null)
	//		{
	//			String message = Logging.getMessage("nullValue.ContextIsNull");
	//			Logging.logger().severe(message);
	//			throw new IllegalArgumentException(message);
	//		}
	//
	//		XPath xpath = WWXML.makeXPath();
	//
	//		// Common layer properties.
	//		AbstractLayer.createLayerConfigElements(params, context);
	//
	//		// LevelSet properties.
	//		DataConfigurationUtils.createLevelSetConfigElements(params, context);
	//
	//		// Service properties.
	//		// Try to get the SERVICE_NAME property, but default to "WWTileService".
	//		String s = AVListImpl.getStringValue(params, AVKey.SERVICE_NAME, "WWTileService");
	//		if (s != null && s.length() > 0)
	//		{
	//			// The service element may already exist, in which case we want to append to it.
	//			Element el = WWXML.getElement(context, "Service", xpath);
	//			if (el == null)
	//				el = WWXML.appendElementPath(context, "Service");
	//			WWXML.setTextAttribute(el, "serviceName", s);
	//		}
	//
	//		WWXML.checkAndAppendBooleanElement(params, AVKey.RETRIEVE_PROPERTIES_FROM_SERVICE, context,
	//				"RetrievePropertiesFromService");
	//
	//		// Image format properties.
	//		WWXML.checkAndAppendTextElement(params, AVKey.IMAGE_FORMAT, context, "ImageFormat");
	//		WWXML.checkAndAppendTextElement(params, AVKey.TEXTURE_FORMAT, context, "TextureFormat");
	//
	//		Object o = params.getValue(AVKey.AVAILABLE_IMAGE_FORMATS);
	//		if (o != null && o instanceof String[])
	//		{
	//			String[] strings = (String[]) o;
	//			if (strings.length > 0)
	//			{
	//				// The available image formats element may already exists, in which case we want to append to it, rather
	//				// than create entirely separate paths.
	//				Element el = WWXML.getElement(context, "AvailableImageFormats", xpath);
	//				if (el == null)
	//					el = WWXML.appendElementPath(context, "AvailableImageFormats");
	//				WWXML.appendTextArray(el, "ImageFormat", strings);
	//			}
	//		}
	//
	//		// Optional behavior properties.
	//		WWXML.checkAndAppendBooleanElement(params, AVKey.FORCE_LEVEL_ZERO_LOADS, context,
	//				"ForceLevelZeroLoads");
	//		WWXML.checkAndAppendBooleanElement(params, AVKey.RETAIN_LEVEL_ZERO_TILES, context,
	//				"RetainLevelZeroTiles");
	//		WWXML.checkAndAppendBooleanElement(params, AVKey.USE_MIP_MAPS, context, "UseMipMaps");
	//		WWXML.checkAndAppendBooleanElement(params, AVKey.USE_TRANSPARENT_TEXTURES, context,
	//				"UseTransparentTextures");
	//		WWXML.checkAndAppendDoubleElement(params, AVKey.SPLIT_SCALE, context, "SplitScale");
	//
	//		// Retrieval properties.
	//		if (params.getValue(AVKey.URL_CONNECT_TIMEOUT) != null
	//				|| params.getValue(AVKey.URL_READ_TIMEOUT) != null
	//				|| params.getValue(AVKey.RETRIEVAL_QUEUE_STALE_REQUEST_LIMIT) != null)
	//		{
	//			Element el = WWXML.getElement(context, "RetrievalTimeouts", xpath);
	//			if (el == null)
	//				el = WWXML.appendElementPath(context, "RetrievalTimeouts");
	//
	//			WWXML.checkAndAppendTimeElement(params, AVKey.URL_CONNECT_TIMEOUT, el,
	//					"ConnectTimeout/Time");
	//			WWXML.checkAndAppendTimeElement(params, AVKey.URL_READ_TIMEOUT, el, "ReadTimeout/Time");
	//			WWXML.checkAndAppendTimeElement(params, AVKey.RETRIEVAL_QUEUE_STALE_REQUEST_LIMIT, el,
	//					"StaleRequestLimit/Time");
	//		}
	//
	//		return context;
	//	}
	//
	//	/**
	//	 * Parses TiledImageLayer configuration parameters from the specified DOM
	//	 * document. This writes output as key-value pairs to params. If a parameter
	//	 * from the XML document already exists in params, that parameter is
	//	 * ignored. Supported key and parameter names are:
	//	 * <table>
	//	 * <tr>
	//	 * <th>Parameter</th>
	//	 * <th>Element Path</th>
	//	 * <th>Type</th>
	//	 * </tr>
	//	 * <tr>
	//	 * <td>{@link AVKey#SERVICE_NAME}</td>
	//	 * <td>Service/@serviceName</td>
	//	 * <td>String</td>
	//	 * </tr>
	//	 * <tr>
	//	 * <td>{@link AVKey#IMAGE_FORMAT}</td>
	//	 * <td>ImageFormat</td>
	//	 * <td>String</td>
	//	 * </tr>
	//	 * <tr>
	//	 * <td>{@link AVKey#AVAILABLE_IMAGE_FORMATS}</td>
	//	 * <td>AvailableImageFormats/ImageFormat</td>
	//	 * <td>String array</td>
	//	 * </tr>
	//	 * <tr>
	//	 * <td>{@link AVKey#FORCE_LEVEL_ZERO_LOADS}</td>
	//	 * <td>ForceLevelZeroLoads</td>
	//	 * <td>Boolean</td>
	//	 * </tr>
	//	 * <tr>
	//	 * <td>{@link AVKey#RETAIN_LEVEL_ZERO_TILES}</td>
	//	 * <td>RetainLevelZeroTiles</td>
	//	 * <td>Boolean</td>
	//	 * </tr>
	//	 * <tr>
	//	 * <td>{@link AVKey#TEXTURE_FORMAT}</td>
	//	 * <td>TextureFormat</td>
	//	 * <td>Boolean</td>
	//	 * </tr>
	//	 * <tr>
	//	 * <td>{@link AVKey#USE_MIP_MAPS}</td>
	//	 * <td>UseMipMaps</td>
	//	 * <td>Boolean</td>
	//	 * </tr>
	//	 * <tr>
	//	 * <td>{@link AVKey#USE_TRANSPARENT_TEXTURES}</td>
	//	 * <td>UseTransparentTextures</td>
	//	 * <td>Boolean</td>
	//	 * </tr>
	//	 * <tr>
	//	 * <td>{@link AVKey#URL_CONNECT_TIMEOUT}</td>
	//	 * <td>RetrievalTimeouts/ConnectTimeout/Time</td>
	//	 * <td>Integer milliseconds</td>
	//	 * </tr>
	//	 * <tr>
	//	 * <td>{@link AVKey#URL_READ_TIMEOUT}</td>
	//	 * <td>RetrievalTimeouts/ReadTimeout/Time</td>
	//	 * <td>Integer milliseconds</td>
	//	 * </tr>
	//	 * <tr>
	//	 * <td>{@link AVKey#RETRIEVAL_QUEUE_STALE_REQUEST_LIMIT}</td>
	//	 * <td>RetrievalTimeouts/StaleRequestLimit/Time</td>
	//	 * <td>Integer milliseconds</td>
	//	 * </tr>
	//	 * </table>
	//	 * This also parses common layer and LevelSet configuration parameters by
	//	 * invoking
	//	 * {@link gov.nasa.worldwind.layers.AbstractLayer#getLayerConfigParams(org.w3c.dom.Element, gov.nasa.worldwind.avlist.AVList)}
	//	 * and
	//	 * {@link gov.nasa.worldwind.util.DataConfigurationUtils#getLevelSetConfigParams(org.w3c.dom.Element, gov.nasa.worldwind.avlist.AVList)}
	//	 * .
	//	 * 
	//	 * @param domElement
	//	 *            the XML document root to parse for TiledImageLayer
	//	 *            configuration parameters.
	//	 * @param params
	//	 *            the output key-value pairs which recieve the TiledImageLayer
	//	 *            configuration parameters. A null reference is permitted.
	//	 * 
	//	 * @return a reference to params, or a new AVList if params is null.
	//	 * 
	//	 * @throws IllegalArgumentException
	//	 *             if the document is null.
	//	 */
	//	public static AVList getTiledImageLayerConfigParams(Element domElement, AVList params)
	//	{
	//		if (domElement == null)
	//		{
	//			String message = Logging.getMessage("nullValue.DocumentIsNull");
	//			Logging.logger().severe(message);
	//			throw new IllegalArgumentException(message);
	//		}
	//
	//		if (params == null)
	//			params = new AVListImpl();
	//
	//		XPath xpath = WWXML.makeXPath();
	//
	//		// Common layer properties.
	//		AbstractLayer.getLayerConfigParams(domElement, params);
	//
	//		// LevelSet properties.
	//		DataConfigurationUtils.getLevelSetConfigParams(domElement, params);
	//
	//		// Service properties.
	//		WWXML.checkAndSetStringParam(domElement, params, AVKey.SERVICE_NAME,
	//				"Service/@serviceName", xpath);
	//		WWXML.checkAndSetBooleanParam(domElement, params, AVKey.RETRIEVE_PROPERTIES_FROM_SERVICE,
	//				"RetrievePropertiesFromService", xpath);
	//
	//		// Image format properties.
	//		WWXML.checkAndSetStringParam(domElement, params, AVKey.IMAGE_FORMAT, "ImageFormat", xpath);
	//		WWXML.checkAndSetStringParam(domElement, params, AVKey.TEXTURE_FORMAT, "TextureFormat",
	//				xpath);
	//		WWXML.checkAndSetUniqueStringsParam(domElement, params, AVKey.AVAILABLE_IMAGE_FORMATS,
	//				"AvailableImageFormats/ImageFormat", xpath);
	//
	//		// Optional behavior properties.
	//		WWXML.checkAndSetBooleanParam(domElement, params, AVKey.FORCE_LEVEL_ZERO_LOADS,
	//				"ForceLevelZeroLoads", xpath);
	//		WWXML.checkAndSetBooleanParam(domElement, params, AVKey.RETAIN_LEVEL_ZERO_TILES,
	//				"RetainLevelZeroTiles", xpath);
	//		WWXML.checkAndSetBooleanParam(domElement, params, AVKey.USE_MIP_MAPS, "UseMipMaps", xpath);
	//		WWXML.checkAndSetBooleanParam(domElement, params, AVKey.USE_TRANSPARENT_TEXTURES,
	//				"UseTransparentTextures", xpath);
	//		WWXML.checkAndSetDoubleParam(domElement, params, AVKey.SPLIT_SCALE, "SplitScale", xpath);
	//		WWXML.checkAndSetColorArrayParam(domElement, params, AVKey.TRANSPARENCY_COLORS,
	//				"TransparencyColors/Color", xpath);
	//
	//		// Retrieval properties. Convert the Long time values to Integers, because BasicTiledImageLayer is expecting
	//		// Integer values.
	//		WWXML.checkAndSetTimeParamAsInteger(domElement, params, AVKey.URL_CONNECT_TIMEOUT,
	//				"RetrievalTimeouts/ConnectTimeout/Time", xpath);
	//		WWXML.checkAndSetTimeParamAsInteger(domElement, params, AVKey.URL_READ_TIMEOUT,
	//				"RetrievalTimeouts/ReadTimeout/Time", xpath);
	//		WWXML.checkAndSetTimeParamAsInteger(domElement, params,
	//				AVKey.RETRIEVAL_QUEUE_STALE_REQUEST_LIMIT,
	//				"RetrievalTimeouts/StaleRequestLimit/Time", xpath);
	//
	//		// Parse the legacy configuration parameters. This enables TiledImageLayer to recognize elements from previous
	//		// versions of configuration documents.
	//		getLegacyTiledImageLayerConfigParams(domElement, params);
	//
	//		return params;
	//	}
	//
	//	/**
	//	 * Parses TiledImageLayer configuration parameters from previous versions of
	//	 * configuration documents. This writes output as key-value pairs to params.
	//	 * If a parameter from the XML document already exists in params, that
	//	 * parameter is ignored. Supported key and parameter names are:
	//	 * <table>
	//	 * <tr>
	//	 * <th>Parameter</th>
	//	 * <th>Element Path</th>
	//	 * <th>Type</th>
	//	 * </tr>
	//	 * <tr>
	//	 * <td>{@link AVKey#TEXTURE_FORMAT}</td>
	//	 * <td>CompressTextures</td>
	//	 * <td>"image/dds" if CompressTextures is "true"; null otherwise</td>
	//	 * </tr>
	//	 * </table>
	//	 * 
	//	 * @param domElement
	//	 *            the XML document root to parse for legacy TiledImageLayer
	//	 *            configuration parameters.
	//	 * @param params
	//	 *            the output key-value pairs which recieve the TiledImageLayer
	//	 *            configuration parameters. A null reference is permitted.
	//	 * 
	//	 * @return a reference to params, or a new AVList if params is null.
	//	 * 
	//	 * @throws IllegalArgumentException
	//	 *             if the document is null.
	//	 */
	//	protected static AVList getLegacyTiledImageLayerConfigParams(Element domElement, AVList params)
	//	{
	//		if (domElement == null)
	//		{
	//			String message = Logging.getMessage("nullValue.DocumentIsNull");
	//			Logging.logger().severe(message);
	//			throw new IllegalArgumentException(message);
	//		}
	//
	//		if (params == null)
	//			params = new AVListImpl();
	//
	//		XPath xpath = WWXML.makeXPath();
	//
	//		Object o = params.getValue(AVKey.TEXTURE_FORMAT);
	//		if (o == null)
	//		{
	//			Boolean b = WWXML.getBoolean(domElement, "CompressTextures", xpath);
	//			if (b != null && b)
	//				params.setValue(AVKey.TEXTURE_FORMAT, "image/dds");
	//		}
	//
	//		return params;
	//	}

	// ============== Image Composition ======================= //
	// ============== Image Composition ======================= //
	// ============== Image Composition ======================= //

	public List<String> getAvailableImageFormats()
	{
		return new ArrayList<String>(this.supportedImageFormats);
	}

	public boolean isImageFormatAvailable(String imageFormat)
	{
		return imageFormat != null && this.supportedImageFormats.contains(imageFormat);
	}

	public String getDefaultImageFormat()
	{
		return this.supportedImageFormats.size() > 0 ? this.supportedImageFormats.get(0) : null;
	}

	protected void setAvailableImageFormats(String[] formats)
	{
		this.supportedImageFormats.clear();

		if (formats != null)
			this.supportedImageFormats.addAll(Arrays.asList(formats));
	}

	protected BufferedImage requestImage(CurtainTextureTile tile, String mimeType)
			throws URISyntaxException, InterruptedIOException, MalformedURLException
	{
		String pathBase = tile.getPathBase();
		String suffix = WWIO.makeSuffixForMimeType(mimeType);
		String path = pathBase + suffix;
		File f = new File(path);
		URL url;
		if (f.isAbsolute() && f.exists())
			url = f.toURI().toURL();
		else
			url = this.getDataFileStore().findFile(path, false);

		if (url == null) // image is not local
			return null;

		if (WWIO.isFileOutOfDate(url, tile.getLevel().getExpiryTime()))
		{
			// The file has expired. Delete it.
			this.getDataFileStore().removeFile(url);
			String message = Logging.getMessage("generic.DataFileExpired", url);
			Logging.logger().fine(message);
		}
		else
		{
			try
			{
				File imageFile = new File(url.toURI());
				BufferedImage image = ImageIO.read(imageFile);
				if (image == null)
				{
					String message = Logging.getMessage("generic.ImageReadFailed", imageFile);
					throw new RuntimeException(message);
				}

				this.levels.unmarkResourceAbsent(tile);
				return image;
			}
			catch (InterruptedIOException e)
			{
				throw e;
			}
			catch (IOException e)
			{
				// Assume that something's wrong with the file and delete it.
				this.getDataFileStore().removeFile(url);
				this.levels.markResourceAbsent(tile);
				String message = Logging.getMessage("generic.DeletedCorruptDataFile", url);
				Logging.logger().info(message);
			}
		}

		return null;
	}

	public int computeLevelForResolution(Sector sector, double resolution)
	{
		if (sector == null)
		{
			String message = Logging.getMessage("nullValue.SectorIsNull");
			Logging.logger().severe(message);
			throw new IllegalStateException(message);
		}

		// Find the first level exceeding the desired resolution
		double texelSize;
		CurtainLevel targetLevel = this.levels.getLastLevel();
		for (int i = 0; i < this.getLevels().getLastLevel().getLevelNumber(); i++)
		{
			if (this.levels.isLevelEmpty(i))
				continue;

			texelSize = this.levels.getLevel(i).getTexelSize();
			if (texelSize > resolution)
				continue;

			targetLevel = this.levels.getLevel(i);
			break;
		}

		// Choose the level closest to the resolution desired
		if (targetLevel.getLevelNumber() != 0
				&& !this.levels.isLevelEmpty(targetLevel.getLevelNumber() - 1))
		{
			CurtainLevel nextLowerLevel = this.levels.getLevel(targetLevel.getLevelNumber() - 1);
			double dless = Math.abs(nextLowerLevel.getTexelSize() - resolution);
			double dmore = Math.abs(targetLevel.getTexelSize() - resolution);
			if (dless < dmore)
				targetLevel = nextLowerLevel;
		}

		Logging.logger().fine(
				Logging.getMessage("layers.TiledImageLayer.LevelSelection",
						targetLevel.getLevelNumber(), Double.toString(targetLevel.getTexelSize())));
		return targetLevel.getLevelNumber();
	}
}