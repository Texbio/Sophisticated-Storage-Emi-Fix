package com.sophfix;

import net.minecraftforge.fml.common.Mod;

/**
 * Client-side Forge mod that cleans up Sophisticated Storage's recipe display in EMI.
 *
 * All work happens inside the {@link com.sophfix.compat.SophFixEmiPlugin} entrypoint,
 * which EMI discovers via the {@code @EmiEntrypoint} annotation. No tick listeners,
 * no screen hooks, no reflection on EMI internals.
 */
@Mod(SophStorageEmiFix.MOD_ID)
public class SophStorageEmiFix {
	public static final String MOD_ID = "sophstorageemifix";
}
