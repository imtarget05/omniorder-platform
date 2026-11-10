package com.omniorder.inventory.grpc;

import com.omniorder.contracts.grpc.inventory.CheckStockReferenceRequest;
import com.omniorder.contracts.grpc.inventory.CheckStockReferenceResponse;
import com.omniorder.contracts.grpc.inventory.GetProductRequest;
import com.omniorder.contracts.grpc.inventory.GetProductResponse;
import com.omniorder.contracts.grpc.inventory.InventoryServiceGrpc;
import com.omniorder.inventory.repository.ProductRepository;
import io.grpc.stub.StreamObserver;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

@Service
public class InventoryGrpcService extends InventoryServiceGrpc.InventoryServiceImplBase {

    private final ProductRepository productRepository;

    public InventoryGrpcService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public void getProduct(GetProductRequest request, StreamObserver<GetProductResponse> responseObserver) {
        var product = productRepository.findBySku(request.getSku());
        if (product.isEmpty()) {
            responseObserver.onNext(GetProductResponse.newBuilder()
                .setExists(false)
                .build());
            responseObserver.onCompleted();
            return;
        }

        var p = product.get();
        BigDecimal price = p.getPrice();
        responseObserver.onNext(GetProductResponse.newBuilder()
            .setProductId(p.getId().toString())
            .setSku(p.getSku())
            .setName(p.getName())
            .setPrice(price.doubleValue())
            .setAvailableStock(p.getAvailableStock())
            .setExists(true)
            .build());
        responseObserver.onCompleted();
    }

    @Override
    public void checkStockReference(CheckStockReferenceRequest request,
                                     StreamObserver<CheckStockReferenceResponse> responseObserver) {
        try {
            var product = productRepository.findById(java.util.UUID.fromString(request.getProductId()));
            if (product.isEmpty()) {
                responseObserver.onNext(CheckStockReferenceResponse.newBuilder()
                    .setProductId(request.getProductId())
                    .setAvailableStock(0)
                    .setSufficient(false)
                    .build());
                responseObserver.onCompleted();
                return;
            }

            var p = product.get();
            boolean sufficient = p.getAvailableStock() >= request.getRequestedQuantity();
            responseObserver.onNext(CheckStockReferenceResponse.newBuilder()
                .setProductId(request.getProductId())
                .setAvailableStock(p.getAvailableStock())
                .setSufficient(sufficient)
                .build());
            responseObserver.onCompleted();
        } catch (IllegalArgumentException e) {
            responseObserver.onError(
                io.grpc.Status.INVALID_ARGUMENT.withDescription("Invalid product ID").asException());
        }
    }
}
