package com.omniorder.order.grpc;

import com.omniorder.contracts.grpc.inventory.CheckStockReferenceRequest;
import com.omniorder.contracts.grpc.inventory.CheckStockReferenceResponse;
import com.omniorder.contracts.grpc.inventory.GetProductRequest;
import com.omniorder.contracts.grpc.inventory.GetProductResponse;
import com.omniorder.contracts.grpc.inventory.InventoryServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.util.UUID;

@Component
public class InventoryGrpcClient {

    private final InventoryServiceGrpc.InventoryServiceBlockingStub blockingStub;
    private final ManagedChannel channel;

    public InventoryGrpcClient(@Value("${inventory.grpc.host:localhost}") String host,
                                @Value("${inventory.grpc.port:9090}") int port) {
        this.channel = ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext()
            .build();
        this.blockingStub = InventoryServiceGrpc.newBlockingStub(channel);
    }

    @PostConstruct
    public void init() {
        // Channel is lazy, no-op
    }

    @PreDestroy
    public void destroy() {
        channel.shutdown();
    }

    public ProductInfo getProduct(String sku) {
        try {
            GetProductResponse response = blockingStub.getProduct(
                GetProductRequest.newBuilder().setSku(sku).build());
            return new ProductInfo(
                response.getProductId(),
                response.getSku(),
                response.getName(),
                response.getPrice(),
                response.getAvailableStock(),
                response.getExists());
        } catch (StatusRuntimeException e) {
            return new ProductInfo(null, sku, null, null, null, false);
        }
    }

    public StockReference checkStock(String productId, int requestedQuantity) {
        try {
            CheckStockReferenceResponse response = blockingStub.checkStockReference(
                CheckStockReferenceRequest.newBuilder()
                    .setProductId(productId)
                    .setRequestedQuantity(requestedQuantity)
                    .build());
            return new StockReference(
                response.getProductId(),
                response.getAvailableStock(),
                response.getSufficient());
        } catch (StatusRuntimeException e) {
            return new StockReference(productId, null, false);
        }
    }

    /**
     * Lấy giá sản phẩm theo productId (UUID). Dùng cho order-service để tính totalPrice,
     * không tin cậy giá từ client.
     */
    public BigDecimal getProductPrice(UUID productId) {
        try {
            GetProductResponse response = blockingStub.getProduct(
                GetProductRequest.newBuilder().setSku(productId.toString()).build());
            if (!response.getExists()) {
                return null;
            }
            return BigDecimal.valueOf(response.getPrice());
        } catch (StatusRuntimeException e) {
            return null;
        }
    }

    public record ProductInfo(
        String productId,
        String sku,
        String name,
        Double price,
        Integer availableStock,
        boolean exists
    ) {}

    public record StockReference(
        String productId,
        Integer availableStock,
        boolean sufficient
    ) {}
}
