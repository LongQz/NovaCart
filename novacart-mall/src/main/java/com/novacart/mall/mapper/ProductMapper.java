package com.novacart.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novacart.mall.entity.Product;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** Product persistence and atomic inventory operations. */
public interface ProductMapper extends BaseMapper<Product> {
    @Update("update product set stock = stock - #{quantity}, update_time = now() "
            + "where id = #{productId} and active = 1 and stock >= #{quantity}")
    int deductStock(@Param("productId") Long productId, @Param("quantity") int quantity);

    @Update("update product set stock = stock - #{quantity}, version = version + 1, update_time = now() "
            + "where id = #{productId} and version = #{version} and active = 1 and stock >= #{quantity}")
    int deductStockWithVersion(
            @Param("productId") Long productId,
            @Param("quantity") int quantity,
            @Param("version") int version
    );

    @Update("update product set stock = stock + #{quantity}, update_time = now() where id = #{productId}")
    int restoreStock(@Param("productId") Long productId, @Param("quantity") int quantity);

    @Update("update product set sales = sales + #{quantity}, update_time = now() where id = #{productId}")
    int increaseSales(@Param("productId") Long productId, @Param("quantity") int quantity);
}
