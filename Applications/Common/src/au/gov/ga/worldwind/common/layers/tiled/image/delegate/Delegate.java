package au.gov.ga.worldwind.common.layers.tiled.image.delegate;

/**
 * Super-interface which all delegates implement. Defines functions which allow
 * saving delegates to a string definition and loading a new instance from a
 * string definition.
 * 
 * @author Michael de Hoog
 */
public interface Delegate
{
	/**
	 * Generate a new Delegate from a string definition. Must return
	 * {@code null} if the provided definition string could not possibly be
	 * generated by the {@code toDefinition()} function.
	 * 
	 * @param definition
	 * @return New delegate if the definition is valid for this delegate
	 */
	Delegate fromDefinition(String definition);

	/**
	 * Generate a definition string for this delegate. Must be a string accepted
	 * by the {@code fromDefinition()} function.
	 * 
	 * @return
	 */
	String toDefinition();
}
