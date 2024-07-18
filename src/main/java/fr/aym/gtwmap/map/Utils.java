package fr.aym.gtwmap.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.I18n;
import net.minecraft.world.chunk.Chunk;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public class Utils
{
	public static int[] integerListToIntArray(List<Integer> list)
	{
		// convert List of integers to integer array
		int size = list.size();
		int[] array = new int[size];
		for (int i = 0; i < size; i++)
		{
			array[i] = list.get(i);
		}
		return array;
	}

	public static File getFreeFilename(File dir, String baseName, String ext)
	{
		int i = 0;
		File outputFile;
		if (dir != null)
		{
			outputFile = new File(dir, baseName + "." + ext);
		}
		else
		{
			outputFile = new File(baseName + "." + ext);
		}
		while (outputFile.exists() && i < 1000)
		{
			if (dir != null)
			{
				outputFile = new File(dir, baseName + "." + i + "." + ext);
			}
			else
			{
				outputFile = new File(baseName + "." + i + "." + ext);
			}
			i++;
		}
		return i < 1000 ? outputFile : null;
	}

	public static File getDimensionDir(File worldDir, int dimension)
	{
		File dimDir;
		if (dimension != 0)
		{
			dimDir = new File(worldDir, "DIM" + dimension);
		}
		else
		{
			dimDir = worldDir;
		}
		return dimDir;
	}

	public static IntBuffer allocateDirectIntBuffer(int size)
	{
		if (size < 1)
		{
			int NewSize = Minecraft.getGLMaximumTextureSize();
			return ByteBuffer.allocateDirect(NewSize * NewSize * 4).order(ByteOrder.nativeOrder()).asIntBuffer();

		}
		return ByteBuffer.allocateDirect(size * 4).order(ByteOrder.nativeOrder()).asIntBuffer();
	}

	// algorithm from http://graphics.stanford.edu/~seander/bithacks.html (Sean
	// Anderson)
	// works by making sure all bits to the right of the highest set bit are 1,
	// then
	// adding 1 to get the answer.
	public static int nextHighestPowerOf2(int v)
	{
		// decrement by 1 (to handle cases where v is already a power of two)
		v--;

		// set all bits to the right of the uppermost set bit.
		v |= v >> 1;
		v |= v >> 2;
		v |= v >> 4;
		v |= v >> 8;
		v |= v >> 16;
		// v |= v >> 32; // uncomment for 64 bit input values

		// add 1 to get the power of two result
		return v + 1;
	}

	public static String getCurrentDateString()
	{
		DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmm");
		return dateFormat.format(new Date());
	}

	public static int distToChunkSq(int x, int z, Chunk chunk)
	{
		int dx = (chunk.x << 4) + 8 - x;
		int dz = (chunk.z << 4) + 8 - z;
		return dx * dx + dz * dz;
	}
	public static String stringArrayToString(String[] arr)
	{
		StringBuilder builder = new StringBuilder();
		for (String s : arr)
		{
			builder.append(I18n.format(s));
			builder.append("\n");
		}
		return builder.toString();
	}

	public static int getMaxWidth(String[] arr, String[] arr2)
	{
		FontRenderer fontRendererObj = Minecraft.getMinecraft().fontRenderer;
		int Width = 1;
		for (int i = 0; i < arr.length; i++)
		{
			int w1 = 0;
			int w2 = 0;

			if (i < arr.length)
			{
				String s = I18n.format(arr[i]);
				w1 = fontRendererObj.getStringWidth(s);
			}
			if (arr2 != null && i < arr2.length)
			{
				String s = I18n.format(arr2[i]);
				w2 = fontRendererObj.getStringWidth(s);
				w2 += 65;
			}
			int wTot = w1 > w2 ? w1 : w2;
			Width = Width > wTot ? Width : wTot;
		}
		return Width;
	}

	private static int[] colours =
			new int[] { 0xff0000, 0x00ff00, 0x0000ff, 0xffff00, 0xff00ff, 0x00ffff, 0xff8000, 0x8000ff };
	// static so that current index is shared between all markers
	public static int colourIndex = 0;

	private static int getColoursLengt()
	{
		return Utils.colours.length;
	}

	public static int getCurrentColour()
	{
		return 0xff000000 | Utils.colours[Utils.colourIndex];
	}

	public static int getNextColour()
	{
		Utils.colourIndex = (Utils.colourIndex + 1) % Utils.getColoursLengt();
		return Utils.getCurrentColour();
	}

	public static int getPrevColour()
	{
		Utils.colourIndex = (Utils.colourIndex + Utils.getColoursLengt() - 1) % Utils.getColoursLengt();
		return Utils.getCurrentColour();
	}

	/*
	 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
	 *
	 * Copyright 1997-2007 Sun Microsystems, Inc. All rights reserved.
	 *
	 * The contents of this file are subject to the terms of either the GNU
	 * General Public License Version 2 only ("GPL") or the Common Development
	 * and Distribution License("CDDL") (collectively, the "License"). You may
	 * not use this file except in compliance with the License. You can obtain a
	 * copy of the License at http://www.netbeans.org/cddl-gplv2.html or
	 * nbbuild/licenses/CDDL-GPL-2-CP. See the License for the specific language
	 * governing permissions and limitations under the License. When
	 * distributing the software, include this License Header Notice in each
	 * file and include the License file at nbbuild/licenses/CDDL-GPL-2-CP. Sun
	 * designates this particular file as subject to the "Classpath" exception
	 * as provided by Sun in the GPL Version 2 section of the License file that
	 * accompanied this code. If applicable, add the following below the License
	 * Header, with the fields enclosed by brackets [] replaced by your own
	 * identifying information:
	 * "Portions Copyrighted [year] [name of copyright owner]"
	 *
	 * Contributor(s):
	 *
	 * The Original Software is NetBeans. The Initial Developer of the Original
	 * Software is Sun Microsystems, Inc. Portions Copyright 1997-2006 Sun
	 * Microsystems, Inc. All Rights Reserved.
	 *
	 * If you wish your version of this file to be governed by only the CDDL or
	 * only the GPL Version 2, indicate your decision by adding "[Contributor]
	 * elects to include this software in this distribution under the [CDDL or
	 * GPL Version 2] license." If you do not indicate a single choice of
	 * license, a recipient has the option to distribute your version of this
	 * file under either the CDDL, the GPL Version 2 or to extend the choice of
	 * license to its licensees as provided above. However, if you add GPL
	 * Version 2 code and therefore, elected the GPL Version 2 license, then the
	 * option applies only if the new code is made subject to such option by the
	 * copyright holder.
	 *
	 * @since 4.37
	 *
	 * @author Jaroslav Tulach
	 */
	/*
	 * Create a typesafe copy of a raw map.
	 *
	 * @param rawMap an unchecked map
	 *
	 * @param keyType the desired supertype of the keys
	 *
	 * @param valueType the desired supertype of the values
	 *
	 * @param strict true to throw a <code>ClassCastException</code> if the raw
	 * map has an invalid key or value, false to skip over such map entries
	 * (warnings may be logged)
	 *
	 * @return a typed map guaranteed to contain only keys and values assignable
	 * to the named types (or they may be null)
	 *
	 * @throws ClassCastException if some key or value in the raw map was not
	 * well-typed, and only if <code>strict</code> was true
	 */
	@SuppressWarnings("rawtypes")
	public static <K, V> Map<K, V> checkedMapByCopy(Map rawMap, Class<K> keyType, Class<V> valueType, boolean strict)
			throws ClassCastException
	{
		Map<K, V> m2 = new HashMap<K, V>(rawMap.size() * 4 / 3 + 1);
		Iterator it = rawMap.entrySet().iterator();
		while (it.hasNext())
		{
			Map.Entry e = (Map.Entry) it.next();
			try
			{
				m2.put(keyType.cast(e.getKey()), valueType.cast(e.getValue()));
			}
			catch (ClassCastException x)
			{
				if (strict)
				{
					throw x;
				}
				else
				{
					System.out.println("not assignable");
				}
			}
		}
		return m2;
	}
}
