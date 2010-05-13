package au.gov.ga.worldwind.components.lazytree;

import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;

public interface ITreeObject
{
	public MutableTreeNode[] getChildren(DefaultTreeModel model);
}