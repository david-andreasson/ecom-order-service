package se.moln.orderservice.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class DtoMiscTest {

    @Test
    void userResponse_getId_prefersUserId_thenId() {
        UserResponse ur = new UserResponse();
        // via reflection set field values since class has no setters
        try {
            var fUserId = UserResponse.class.getDeclaredField("userId");
            fUserId.setAccessible(true);
            var fId = UserResponse.class.getDeclaredField("id");
            fId.setAccessible(true);

            java.util.UUID uid = java.util.UUID.randomUUID();
            java.util.UUID iid = java.util.UUID.randomUUID();
            fUserId.set(ur, uid);
            fId.set(ur, iid);
            assertEquals(uid, ur.getId());

            // Now clear userId, expect fallback to id
            fUserId.set(ur, null);
            assertEquals(iid, ur.getId());
        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    void adjustStockRequest_record_holdsValue() {
        AdjustStockRequest req = new AdjustStockRequest(5);
        assertEquals(5, req.delta());
    }
}
