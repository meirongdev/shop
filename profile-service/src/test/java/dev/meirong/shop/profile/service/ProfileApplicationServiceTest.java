package dev.meirong.shop.profile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.contracts.api.ProfileApi;
import dev.meirong.shop.profile.domain.BuyerProfileRepository;
import dev.meirong.shop.profile.domain.SellerProfileEntity;
import dev.meirong.shop.profile.domain.SellerProfileRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProfileApplicationServiceTest {

    @Mock
    private BuyerProfileRepository buyerRepository;

    @Mock
    private SellerProfileRepository sellerRepository;

    @InjectMocks
    private ProfileApplicationService service;

    @Test
    void getSellerProfile_withSeededSeller_returnsProfile() {
        SellerProfileEntity seller = new SellerProfileEntity(
                "seller-2001",
                "seller.demo",
                "Seller Demo",
                "seller.demo@example.com",
                "SILVER"
        );
        when(sellerRepository.findById("seller-2001")).thenReturn(Optional.of(seller));

        ProfileApi.ProfileResponse response = service.getSellerProfile(new ProfileApi.GetProfileRequest("seller-2001"));

        assertThat(response.playerId()).isEqualTo("seller-2001");
        assertThat(response.username()).isEqualTo("seller.demo");
        assertThat(response.displayName()).isEqualTo("Seller Demo");
        verifyNoInteractions(buyerRepository);
    }

    @Test
    void updateSellerProfile_updatesSellerRecord() {
        SellerProfileEntity seller = new SellerProfileEntity(
                "seller-2001",
                "seller.demo",
                "Seller Demo",
                "seller.demo@example.com",
                "SILVER"
        );
        ProfileApi.UpdateProfileRequest request = new ProfileApi.UpdateProfileRequest(
                "seller-2001",
                "Seller Studio",
                "studio@example.com",
                "GOLD"
        );
        when(sellerRepository.findById("seller-2001")).thenReturn(Optional.of(seller));
        when(sellerRepository.save(seller)).thenReturn(seller);

        ProfileApi.ProfileResponse response = service.updateSellerProfile(request);

        assertThat(response.playerId()).isEqualTo("seller-2001");
        assertThat(response.displayName()).isEqualTo("Seller Studio");
        assertThat(response.email()).isEqualTo("studio@example.com");
        assertThat(response.tier()).isEqualTo("GOLD");
    }

    @Test
    void getSellerProfile_whenSellerMissing_throwsNotFound() {
        when(sellerRepository.findById("seller-404")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSellerProfile(new ProfileApi.GetProfileRequest("seller-404")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Seller profile not found: seller-404");
    }

    @Test
    void getSellerStorefront_returnsShopFields() {
        SellerProfileEntity seller = new SellerProfileEntity(
                "seller-2001", "seller.demo", "Seller Demo", "seller@example.com", "GOLD");
        seller.updateShop("Demo Shop", "demo-shop", "Best products here", null, null);
        seller.updateAvgRating(4.5);
        seller.incrementSales(100);
        when(sellerRepository.findById("seller-2001")).thenReturn(Optional.of(seller));

        ProfileApi.SellerStorefrontResponse response = service.getSellerStorefront("seller-2001");

        assertThat(response.sellerId()).isEqualTo("seller-2001");
        assertThat(response.shopName()).isEqualTo("Demo Shop");
        assertThat(response.shopSlug()).isEqualTo("demo-shop");
        assertThat(response.shopDescription()).isEqualTo("Best products here");
        assertThat(response.avgRating()).isEqualTo(4.5);
        assertThat(response.totalSales()).isEqualTo(100);
        assertThat(response.tier()).isEqualTo("GOLD");
    }

    @Test
    void updateShop_updatesShopFields() {
        SellerProfileEntity seller = new SellerProfileEntity(
                "seller-2001", "seller.demo", "Seller Demo", "seller@example.com", "SILVER");
        ProfileApi.UpdateShopRequest request = new ProfileApi.UpdateShopRequest(
                "seller-2001", "My New Shop", "my-new-shop", "A great shop", null, null);
        when(sellerRepository.findById("seller-2001")).thenReturn(Optional.of(seller));
        when(sellerRepository.save(seller)).thenReturn(seller);

        ProfileApi.SellerStorefrontResponse response = service.updateShop(request);

        assertThat(response.shopName()).isEqualTo("My New Shop");
        assertThat(response.shopSlug()).isEqualTo("my-new-shop");
        assertThat(response.shopDescription()).isEqualTo("A great shop");
    }
}
