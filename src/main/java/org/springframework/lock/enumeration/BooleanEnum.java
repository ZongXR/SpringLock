package org.springframework.lock.enumeration;

/**
 * 适用于boolean的枚举类型
 */
public enum BooleanEnum {
    /**
     * 枚举值
     */
    TRUE(Boolean.TRUE),
    FALSE(Boolean.FALSE),
    NULL(null)
    ;

    /**
     * 每个注解对应的真实值
     */
    private Boolean value = null;

    /**
     * 构造方法
     * @param value 每个注解对应的真实值
     */
    private BooleanEnum(Boolean value) {
        this.value = value;
    }

    /**
     * 获取枚举真实值
     * @return 枚举真实值
     */
    public Boolean getValue() {
        return value;
    }
}
