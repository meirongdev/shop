package dev.meirong.shop.sellerportal.controller

import dev.meirong.shop.sellerportal.model.*
import dev.meirong.shop.sellerportal.service.SellerPortalApiClient
import jakarta.servlet.http.HttpSession
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.view.RedirectView

@Controller
class SellerPortalController(
    private val apiClient: SellerPortalApiClient
) {
    private fun session(session: HttpSession): SellerSession? =
        session.getAttribute("sellerSession") as? SellerSession

    private fun seeOther(url: String) = RedirectView(url).apply { setStatusCode(HttpStatus.SEE_OTHER) }

    // ── Auth ──

    @GetMapping("/seller")
    fun entry(): String = "redirect:/seller/login"

    @GetMapping("/seller/login")
    fun loginPage(model: Model): String {
        model.addAttribute("loginForm", SellerLoginForm())
        return "seller-login"
    }

    @PostMapping("/seller/login")
    fun login(@ModelAttribute loginForm: SellerLoginForm, session: HttpSession): RedirectView {
        session.setAttribute("sellerSession", apiClient.login(loginForm.username, loginForm.password))
        return seeOther("/seller/dashboard")
    }

    @GetMapping("/seller/logout")
    fun logout(session: HttpSession): String {
        session.invalidate()
        return "redirect:/seller/login"
    }

    // ── Dashboard ──

    @GetMapping("/seller/dashboard")
    fun dashboard(model: Model, session: HttpSession): String {
        val s = session(session) ?: return "redirect:/seller/login"
        val dashboard = apiClient.dashboard(s)
        val recentOrders = try { apiClient.listOrders(s).take(5) } catch (_: Exception) { emptyList() }
        model.addAttribute("sessionUser", s)
        model.addAttribute("dashboard", dashboard)
        model.addAttribute("recentOrders", recentOrders)
        model.addAttribute("productForm", ProductForm())
        model.addAttribute("promotionForm", PromotionForm())
        return "seller-dashboard"
    }

    // ── Products ──

    @GetMapping("/seller/products")
    fun products(model: Model, session: HttpSession): String {
        val s = session(session) ?: return "redirect:/seller/login"
        val dashboard = apiClient.dashboard(s)
        model.addAttribute("sessionUser", s)
        model.addAttribute("products", dashboard.products())
        model.addAttribute("productForm", ProductForm())
        return "seller-products"
    }

    @PostMapping("/seller/product")
    fun createProduct(@ModelAttribute productForm: ProductForm, session: HttpSession): RedirectView {
        val s = session(session) ?: return seeOther("/seller/login")
        apiClient.createProduct(s, productForm)
        return seeOther("/seller/products")
    }

    // ── Orders ──

    @GetMapping("/seller/orders")
    fun orders(model: Model, session: HttpSession): String {
        val s = session(session) ?: return "redirect:/seller/login"
        val orders = apiClient.listOrders(s)
        model.addAttribute("sessionUser", s)
        model.addAttribute("orders", orders)
        return "seller-orders"
    }

    @GetMapping("/seller/order/{id}")
    fun orderDetail(@PathVariable id: String, model: Model, session: HttpSession): String {
        val s = session(session) ?: return "redirect:/seller/login"
        val order = apiClient.getOrder(s, id)
        model.addAttribute("sessionUser", s)
        model.addAttribute("order", order)
        return "seller-order-detail"
    }

    @PostMapping("/seller/order/ship")
    fun shipOrder(@RequestParam orderId: String, session: HttpSession): RedirectView {
        val s = session(session) ?: return seeOther("/seller/login")
        apiClient.shipOrder(s, orderId)
        return seeOther("/seller/orders")
    }

    @PostMapping("/seller/order/deliver")
    fun deliverOrder(@RequestParam orderId: String, session: HttpSession): RedirectView {
        val s = session(session) ?: return seeOther("/seller/login")
        apiClient.deliverOrder(s, orderId)
        return seeOther("/seller/orders")
    }

    // ── Promotions & Coupons ──

    @GetMapping("/seller/promotions")
    fun promotions(model: Model, session: HttpSession): String {
        val s = session(session) ?: return "redirect:/seller/login"
        val dashboard = apiClient.dashboard(s)
        val coupons = try { apiClient.listCoupons(s) } catch (_: Exception) { emptyList() }
        model.addAttribute("sessionUser", s)
        model.addAttribute("promotions", dashboard.promotions())
        model.addAttribute("coupons", coupons)
        model.addAttribute("promotionForm", PromotionForm())
        model.addAttribute("couponForm", CouponForm())
        return "seller-promotions"
    }

    @PostMapping("/seller/promotion")
    fun createPromotion(@ModelAttribute promotionForm: PromotionForm, session: HttpSession): RedirectView {
        val s = session(session) ?: return seeOther("/seller/login")
        apiClient.createPromotion(s, promotionForm)
        return seeOther("/seller/promotions")
    }

    @PostMapping("/seller/coupon")
    fun createCoupon(@ModelAttribute couponForm: CouponForm, session: HttpSession): RedirectView {
        val s = session(session) ?: return seeOther("/seller/login")
        apiClient.createCoupon(s, couponForm)
        return seeOther("/seller/promotions")
    }

    // ── Shop Management ──

    @GetMapping("/seller/shop")
    fun shopSettings(model: Model, session: HttpSession): String {
        val s = session(session) ?: return "redirect:/seller/login"
        val shop = try { apiClient.getShop(s) } catch (_: Exception) { null }
        model.addAttribute("sessionUser", s)
        model.addAttribute("shop", shop)
        model.addAttribute("shopForm", ShopForm(
            shopName = shop?.shopName() ?: "",
            shopDescription = shop?.shopDescription() ?: "",
            logoUrl = shop?.logoUrl() ?: "",
            bannerUrl = shop?.bannerUrl() ?: ""
        ))
        return "seller-shop"
    }

    @PostMapping("/seller/shop")
    fun updateShop(@ModelAttribute shopForm: ShopForm, session: HttpSession): RedirectView {
        val s = session(session) ?: return seeOther("/seller/login")
        apiClient.updateShop(s, shopForm.shopName, shopForm.shopDescription, shopForm.logoUrl, shopForm.bannerUrl)
        return seeOther("/seller/shop")
    }
}
