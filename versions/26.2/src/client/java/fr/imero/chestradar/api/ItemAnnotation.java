package fr.imero.chestradar.api;

import net.minecraft.world.item.ItemStack;

import java.util.List;

public interface ItemAnnotation {
	String revision();

	List<AnnotationSegment> annotate(ItemStack stack, long count);
}
