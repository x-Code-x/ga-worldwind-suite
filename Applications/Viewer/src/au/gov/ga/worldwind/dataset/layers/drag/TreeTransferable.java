package au.gov.ga.worldwind.dataset.layers.drag;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;

import javax.swing.JTree;
import javax.swing.tree.TreePath;

public class TreeTransferable implements Transferable
{
	private JTree source;
	private TreePath[] paths;
	private static final DataFlavor[] flavors = new DataFlavor[] { DataFlavor.stringFlavor };

	public TreeTransferable(JTree source, TreePath... data)
	{
		this.source = source;
		this.paths = data;
	}

	public JTree getSource()
	{
		return source;
	}

	public TreePath[] getPaths()
	{
		return paths;
	}

	@Override
	public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException
	{
		return source.toString(); //useless, but at least serializable
	}

	@Override
	public DataFlavor[] getTransferDataFlavors()
	{
		return flavors;
	}

	@Override
	public boolean isDataFlavorSupported(DataFlavor flavor)
	{
		return flavor == DataFlavor.stringFlavor;
	}
}
