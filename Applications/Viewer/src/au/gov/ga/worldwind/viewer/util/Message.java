package au.gov.ga.worldwind.viewer.util;

import au.gov.ga.worldwind.common.util.message.MessageSourceAccessor;
import au.gov.ga.worldwind.viewer.data.messages.ViewerMessageConstants;

public class Message extends ViewerMessageConstants
{
	public static String getMessage(String key)
	{
		return MessageSourceAccessor.getMessage(key);
	}

	public static String getMessage(String key, Object... params)
	{
		return MessageSourceAccessor.getMessage(key, params);
	}
}
