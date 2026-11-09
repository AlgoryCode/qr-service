package com.algoryqr.shared.ui

import androidx.compose.ui.graphics.vector.ImageVector
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.Armchair
import com.adamglin.phosphoricons.regular.SlidersHorizontal
import com.adamglin.phosphoricons.regular.CaretDown
import com.adamglin.phosphoricons.regular.PencilSimple
import com.adamglin.phosphoricons.regular.PicnicTable
import com.adamglin.phosphoricons.regular.ArrowCounterClockwise
import com.adamglin.phosphoricons.regular.ArrowLeft
import com.adamglin.phosphoricons.regular.ArrowsClockwise
import com.adamglin.phosphoricons.regular.Bell
import com.adamglin.phosphoricons.regular.BowlFood
import com.adamglin.phosphoricons.regular.Buildings
import com.adamglin.phosphoricons.regular.CalendarCheck
import com.adamglin.phosphoricons.regular.CaretRight
import com.adamglin.phosphoricons.regular.ChartBar
import com.adamglin.phosphoricons.regular.ChartLineUp
import com.adamglin.phosphoricons.regular.Check
import com.adamglin.phosphoricons.regular.CheckCircle
import com.adamglin.phosphoricons.regular.ChefHat
import com.adamglin.phosphoricons.regular.Clock
import com.adamglin.phosphoricons.regular.Coins
import com.adamglin.phosphoricons.regular.DotsThreeOutline
import com.adamglin.phosphoricons.regular.CookingPot
import com.adamglin.phosphoricons.regular.CreditCard
import com.adamglin.phosphoricons.regular.ForkKnife
import com.adamglin.phosphoricons.regular.HandCoins
import com.adamglin.phosphoricons.regular.Hash
import com.adamglin.phosphoricons.regular.Key
import com.adamglin.phosphoricons.regular.ListBullets
import com.adamglin.phosphoricons.regular.Lock
import com.adamglin.phosphoricons.regular.MagnifyingGlass
import com.adamglin.phosphoricons.regular.MapPin
import com.adamglin.phosphoricons.regular.Minus
import com.adamglin.phosphoricons.regular.Money
import com.adamglin.phosphoricons.regular.Moped
import com.adamglin.phosphoricons.regular.NotePencil
import com.adamglin.phosphoricons.regular.Phone
import com.adamglin.phosphoricons.regular.Plus
import com.adamglin.phosphoricons.regular.Receipt
import com.adamglin.phosphoricons.regular.SignOut
import com.adamglin.phosphoricons.regular.Storefront
import com.adamglin.phosphoricons.regular.Timer
import com.adamglin.phosphoricons.regular.Truck
import com.adamglin.phosphoricons.regular.User
import com.adamglin.phosphoricons.regular.UserCircle
import com.adamglin.phosphoricons.regular.Users
import com.adamglin.phosphoricons.regular.Wallet
import com.adamglin.phosphoricons.regular.X
import com.adamglin.phosphoricons.regular.XCircle

/** Thin-stroke Phosphor icons used across both apps, named after what they mean here. */
object AppIcons {
    val Back: ImageVector get() = PhosphorIcons.Regular.ArrowLeft
    val Next: ImageVector get() = PhosphorIcons.Regular.CaretRight
    val Close: ImageVector get() = PhosphorIcons.Regular.X
    val Actions: ImageVector get() = PhosphorIcons.Regular.SlidersHorizontal
    val Expand: ImageVector get() = PhosphorIcons.Regular.CaretDown
    val Edit: ImageVector get() = PhosphorIcons.Regular.PencilSimple
    val Add: ImageVector get() = PhosphorIcons.Regular.Plus
    val Remove: ImageVector get() = PhosphorIcons.Regular.Minus
    val Refresh: ImageVector get() = PhosphorIcons.Regular.ArrowsClockwise
    val Search: ImageVector get() = PhosphorIcons.Regular.MagnifyingGlass
    val Undo: ImageVector get() = PhosphorIcons.Regular.ArrowCounterClockwise
    val Check: ImageVector get() = PhosphorIcons.Regular.Check
    val Approve: ImageVector get() = PhosphorIcons.Regular.CheckCircle
    val Reject: ImageVector get() = PhosphorIcons.Regular.XCircle
    val Note: ImageVector get() = PhosphorIcons.Regular.NotePencil
    val Lock: ImageVector get() = PhosphorIcons.Regular.Lock
    val Password: ImageVector get() = PhosphorIcons.Regular.Key
    val SignOut: ImageVector get() = PhosphorIcons.Regular.SignOut

    val Dashboard: ImageVector get() = PhosphorIcons.Regular.ChartLineUp
    val Report: ImageVector get() = PhosphorIcons.Regular.ChartBar
    val More: ImageVector get() = PhosphorIcons.Regular.DotsThreeOutline
    val Orders: ImageVector get() = PhosphorIcons.Regular.ListBullets
    val Table: ImageVector get() = PhosphorIcons.Regular.Armchair
    /** The floor plan tab; a table reads better than a seat at tab size. */
    val Tables: ImageVector get() = PhosphorIcons.Regular.PicnicTable
    val Staff: ImageVector get() = PhosphorIcons.Regular.Users
    val Profile: ImageVector get() = PhosphorIcons.Regular.User
    val Account: ImageVector get() = PhosphorIcons.Regular.UserCircle
    val Business: ImageVector get() = PhosphorIcons.Regular.Storefront
    val Branch: ImageVector get() = PhosphorIcons.Regular.Buildings
    val Reservation: ImageVector get() = PhosphorIcons.Regular.CalendarCheck
    val Menu: ImageVector get() = PhosphorIcons.Regular.ForkKnife
    val Product: ImageVector get() = PhosphorIcons.Regular.BowlFood
    val Kitchen: ImageVector get() = PhosphorIcons.Regular.ChefHat
    val Preparing: ImageVector get() = PhosphorIcons.Regular.CookingPot
    val Courier: ImageVector get() = PhosphorIcons.Regular.Moped
    val Delivery: ImageVector get() = PhosphorIcons.Regular.Truck
    val Address: ImageVector get() = PhosphorIcons.Regular.MapPin
    val Phone: ImageVector get() = PhosphorIcons.Regular.Phone
    val Bill: ImageVector get() = PhosphorIcons.Regular.Receipt
    val Cash: ImageVector get() = PhosphorIcons.Regular.Money
    val Card: ImageVector get() = PhosphorIcons.Regular.CreditCard
    val Wallet: ImageVector get() = PhosphorIcons.Regular.Wallet
    val Commission: ImageVector get() = PhosphorIcons.Regular.HandCoins
    val Revenue: ImageVector get() = PhosphorIcons.Regular.Coins
    val Shift: ImageVector get() = PhosphorIcons.Regular.Timer
    val Time: ImageVector get() = PhosphorIcons.Regular.Clock
    val Number: ImageVector get() = PhosphorIcons.Regular.Hash
    val Notification: ImageVector get() = PhosphorIcons.Regular.Bell
}
