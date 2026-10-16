package dev.kdevy.emirecipepins;

/**
 * 让 pin 渲染器能够调整收藏栏每页的条目容量。
 * <p>
 * EMI 的 {@code ScreenSpace.pageSize} 是 final 字段，只有 mixin 才能写入。而 mixin 类本身在
 * 运行时不能从普通类里引用，mixin 包里的普通类同样禁止外部引用，所以这个接口必须待在
 * mixins.json 声明的 package 之外，由 ScreenSpaceMixin 实现。
 */
public interface PageSizeAccess {
    void emiRecipePins$setPageSize(int size);
}
