package com.example.ui.theme

import androidx.compose.ui.graphics.Color

data class AppThemeColor(
    val id: String,
    val displayName: String,
    val primary: Color,
    val primaryVariant: Color,
    val glowColor: Color
) {
    val isCustom: Boolean
        get() = id.startsWith("custom_", ignoreCase = true)

    fun primaryForTheme(isDarkTheme: Boolean): Color {
        return primary.toAdaptivePrimary(isDarkTheme)
    }

    fun primaryVariantForTheme(isDarkTheme: Boolean): Color {
        return primaryVariant.toAdaptivePrimary(isDarkTheme)
    }

    companion object {
        // =====================================================================================
        // --- 100 PRESETS PARA TEMA OSCURO (Vibrantes, luminosos, alto contraste) ---
        // =====================================================================================
        // 1. Azules (1-10)
        val DARK_01 = AppThemeColor("dark_01", "#2AABEE", Color(0xFF2AABEE), Color(0xFF1565C0), Color(0x662AABEE))
        val DARK_02 = AppThemeColor("dark_02", "#38BDF8", Color(0xFF38BDF8), Color(0xFF0284C7), Color(0x6638BDF8))
        val DARK_03 = AppThemeColor("dark_03", "#00D2FF", Color(0xFF00D2FF), Color(0xFF0284C7), Color(0x6600D2FF))
        val DARK_04 = AppThemeColor("dark_04", "#60A5FA", Color(0xFF60A5FA), Color(0xFF2563EB), Color(0x6660A5FA))
        val DARK_05 = AppThemeColor("dark_05", "#3B82F6", Color(0xFF3B82F6), Color(0xFF1D4ED8), Color(0x663B82F6))
        val DARK_06 = AppThemeColor("dark_06", "#2563EB", Color(0xFF2563EB), Color(0xFF1E40AF), Color(0x662563EB))
        val DARK_07 = AppThemeColor("dark_07", "#93C5FD", Color(0xFF93C5FD), Color(0xFF3B82F6), Color(0x6693C5FD))
        val DARK_08 = AppThemeColor("dark_08", "#1E90FF", Color(0xFF1E90FF), Color(0xFF1C86EE), Color(0x661E90FF))
        val DARK_09 = AppThemeColor("dark_09", "#1D4ED8", Color(0xFF1D4ED8), Color(0xFF1E3A8A), Color(0x661D4ED8))
        val DARK_10 = AppThemeColor("dark_10", "#BFDBFE", Color(0xFFBFDBFE), Color(0xFF60A5FA), Color(0x66BFDBFE))

        // 2. Cianes y Turquesas (11-20) - Incluye el Teal del sistema #14B8A6
        val DARK_11 = AppThemeColor("dark_11", "#06B6D4", Color(0xFF06B6D4), Color(0xFF0E7490), Color(0x6606B6D4))
        val DARK_TEAL = AppThemeColor("dark_teal", "#14B8A6", Color(0xFF14B8A6), Color(0xFF0F766E), Color(0x6614B8A6))
        val DARK_13 = AppThemeColor("dark_13", "#22D3EE", Color(0xFF22D3EE), Color(0xFF0891B2), Color(0x6622D3EE))
        val DARK_14 = AppThemeColor("dark_14", "#2DD4BF", Color(0xFF2DD4BF), Color(0xFF115E59), Color(0x662DD4BF))
        val DARK_15 = AppThemeColor("dark_15", "#5EEAD4", Color(0xFF5EEAD4), Color(0xFF0D9488), Color(0x665EEAD4))
        val DARK_16 = AppThemeColor("dark_16", "#00E5FF", Color(0xFF00E5FF), Color(0xFF00B4D8), Color(0x6600E5FF))
        val DARK_17 = AppThemeColor("dark_17", "#00CED1", Color(0xFF00CED1), Color(0xFF20B2AA), Color(0x6600CED1))
        val DARK_18 = AppThemeColor("dark_18", "#A5F3FC", Color(0xFFA5F3FC), Color(0xFF06B6D4), Color(0x66A5F3FC))
        val DARK_19 = AppThemeColor("dark_19", "#99F6E4", Color(0xFF99F6E4), Color(0xFF14B8A6), Color(0x6699F6E4))
        val DARK_20 = AppThemeColor("dark_20", "#67E8F9", Color(0xFF67E8F9), Color(0xFF0891B2), Color(0x6667E8F9))

        // 3. Verdes y Esmeraldas (21-30)
        val DARK_21 = AppThemeColor("dark_21", "#10B981", Color(0xFF10B981), Color(0xFF047857), Color(0x6610B981))
        val DARK_22 = AppThemeColor("dark_22", "#34D399", Color(0xFF34D399), Color(0xFF059669), Color(0x6634D399))
        val DARK_23 = AppThemeColor("dark_23", "#059669", Color(0xFF059669), Color(0xFF047857), Color(0x66059669))
        val DARK_24 = AppThemeColor("dark_24", "#6EE7B7", Color(0xFF6EE7B7), Color(0xFF10B981), Color(0x666EE7B7))
        val DARK_25 = AppThemeColor("dark_25", "#4ADE80", Color(0xFF4ADE80), Color(0xFF16A34A), Color(0x664ADE80))
        val DARK_26 = AppThemeColor("dark_26", "#22C55E", Color(0xFF22C55E), Color(0xFF15803D), Color(0x6622C55E))
        val DARK_27 = AppThemeColor("dark_27", "#00FF7F", Color(0xFF00FF7F), Color(0xFF00CD66), Color(0x6600FF7F))
        val DARK_28 = AppThemeColor("dark_28", "#00E676", Color(0xFF00E676), Color(0xFF00C853), Color(0x6600E676))
        val DARK_29 = AppThemeColor("dark_29", "#10E79D", Color(0xFF10E79D), Color(0xFF059669), Color(0x6610E79D))
        val DARK_30 = AppThemeColor("dark_30", "#A7F3D0", Color(0xFFA7F3D0), Color(0xFF10B981), Color(0x66A7F3D0))

        // 4. Limas y Olivas (31-40)
        val DARK_31 = AppThemeColor("dark_31", "#84CC16", Color(0xFF84CC16), Color(0xFF4D7C0F), Color(0x6684CC16))
        val DARK_32 = AppThemeColor("dark_32", "#A3E635", Color(0xFFA3E635), Color(0xFF65A30D), Color(0x66A3E635))
        val DARK_33 = AppThemeColor("dark_33", "#BEF264", Color(0xFFBEF264), Color(0xFF4D7C0F), Color(0x66BEF264))
        val DARK_34 = AppThemeColor("dark_34", "#D9F99D", Color(0xFFD9F99D), Color(0xFF65A30D), Color(0x66D9F99D))
        val DARK_35 = AppThemeColor("dark_35", "#76FF03", Color(0xFF76FF03), Color(0xFF64DD17), Color(0x6676FF03))
        val DARK_36 = AppThemeColor("dark_36", "#6EE76E", Color(0xFF6EE76E), Color(0xFF16A34A), Color(0x666EE76E))
        val DARK_37 = AppThemeColor("dark_37", "#64DD17", Color(0xFF64DD17), Color(0xFF33691E), Color(0x6664DD17))
        val DARK_38 = AppThemeColor("dark_38", "#AEEA00", Color(0xFFAEEA00), Color(0xFF827717), Color(0x66AEEA00))
        val DARK_39 = AppThemeColor("dark_39", "#C6FF00", Color(0xFFC6FF00), Color(0xFFAEEA00), Color(0x66C6FF00))
        val DARK_40 = AppThemeColor("dark_40", "#ECFCCB", Color(0xFFECFCCB), Color(0xFF84CC16), Color(0x66ECFCCB))

        // 5. Amarillos y Dorados (41-50)
        val DARK_41 = AppThemeColor("dark_41", "#FACC15", Color(0xFFFACC15), Color(0xFFCA8A04), Color(0x66FACC15))
        val DARK_42 = AppThemeColor("dark_42", "#FDE047", Color(0xFFFDE047), Color(0xFFEAB308), Color(0x66FDE047))
        val DARK_43 = AppThemeColor("dark_43", "#F59E0B", Color(0xFFF59E0B), Color(0xFFB45309), Color(0x66F59E0B))
        val DARK_44 = AppThemeColor("dark_44", "#FBBF24", Color(0xFFFBBF24), Color(0xFFD97706), Color(0x66FBBF24))
        val DARK_45 = AppThemeColor("dark_45", "#EAB308", Color(0xFFEAB308), Color(0xFFA16207), Color(0x66EAB308))
        val DARK_46 = AppThemeColor("dark_46", "#FFEA00", Color(0xFFFFEA00), Color(0xFFFFD600), Color(0x66FFEA00))
        val DARK_47 = AppThemeColor("dark_47", "#FFD600", Color(0xFFFFD600), Color(0xFFFFAB00), Color(0x66FFD600))
        val DARK_48 = AppThemeColor("dark_48", "#FFC107", Color(0xFFFFC107), Color(0xFFFFA000), Color(0x66FFC107))
        val DARK_49 = AppThemeColor("dark_49", "#FEF08A", Color(0xFFFEF08A), Color(0xFFFACC15), Color(0x66FEF08A))
        val DARK_50 = AppThemeColor("dark_50", "#D97706", Color(0xFFD97706), Color(0xFFB45309), Color(0x66D97706))

        // 6. Naranjas y Mandarinas (51-60)
        val DARK_51 = AppThemeColor("dark_51", "#FF7A00", Color(0xFFFF7A00), Color(0xFFD84315), Color(0x66FF7A00))
        val DARK_52 = AppThemeColor("dark_52", "#FB923C", Color(0xFFFB923C), Color(0xFFC2410C), Color(0x66FB923C))
        val DARK_53 = AppThemeColor("dark_53", "#F97316", Color(0xFFF97316), Color(0xFFEA580C), Color(0x66F97316))
        val DARK_54 = AppThemeColor("dark_54", "#FF5722", Color(0xFFFF5722), Color(0xFFC23616), Color(0x66FF5722))
        val DARK_55 = AppThemeColor("dark_55", "#EA580C", Color(0xFFEA580C), Color(0xFF9A3412), Color(0x66EA580C))
        val DARK_56 = AppThemeColor("dark_56", "#FF6D00", Color(0xFFFF6D00), Color(0xFFDD2C00), Color(0x66FF6D00))
        val DARK_57 = AppThemeColor("dark_57", "#FF8A65", Color(0xFFFF8A65), Color(0xFFD84315), Color(0x66FF8A65))
        val DARK_58 = AppThemeColor("dark_58", "#FFAB40", Color(0xFFFFAB40), Color(0xFFFF6D00), Color(0x66FFAB40))
        val DARK_59 = AppThemeColor("dark_59", "#FDBA74", Color(0xFFFDBA74), Color(0xFFF97316), Color(0x66FDBA74))
        val DARK_60 = AppThemeColor("dark_60", "#FF9100", Color(0xFFFF9100), Color(0xFFFF6D00), Color(0x66FF9100))

        // 7. Rojos y Carmesíes (61-70)
        val DARK_61 = AppThemeColor("dark_61", "#FF3B30", Color(0xFFFF3B30), Color(0xFF991B1B), Color(0x66FF3B30))
        val DARK_62 = AppThemeColor("dark_62", "#F43F5E", Color(0xFFF43F5E), Color(0xFFBE123C), Color(0x66F43F5E))
        val DARK_63 = AppThemeColor("dark_63", "#FB7185", Color(0xFFFB7185), Color(0xFFE11D48), Color(0x66FB7185))
        val DARK_64 = AppThemeColor("dark_64", "#EF4444", Color(0xFFEF4444), Color(0xFFB91C1C), Color(0x66EF4444))
        val DARK_65 = AppThemeColor("dark_65", "#DC2626", Color(0xFFDC2626), Color(0xFF991B1B), Color(0x66DC2626))
        val DARK_66 = AppThemeColor("dark_66", "#FF1744", Color(0xFFFF1744), Color(0xFFD50000), Color(0x66FF1744))
        val DARK_67 = AppThemeColor("dark_67", "#FF5252", Color(0xFFFF5252), Color(0xFFFF1744), Color(0x66FF5252))
        val DARK_68 = AppThemeColor("dark_68", "#E11D48", Color(0xFFE11D48), Color(0xFF9F1239), Color(0x66E11D48))
        val DARK_69 = AppThemeColor("dark_69", "#FDA4AF", Color(0xFFFDA4AF), Color(0xFFF43F5E), Color(0x66FDA4AF))
        val DARK_70 = AppThemeColor("dark_70", "#FF2D55", Color(0xFFFF2D55), Color(0xFFC2185B), Color(0x66FF2D55))

        // 8. Rosas y Magentas (71-80)
        val DARK_71 = AppThemeColor("dark_71", "#EC4899", Color(0xFFEC4899), Color(0xFFBE185D), Color(0x66EC4899))
        val DARK_72 = AppThemeColor("dark_72", "#D946EF", Color(0xFFD946EF), Color(0xFFA21CAF), Color(0x66D946EF))
        val DARK_73 = AppThemeColor("dark_73", "#F472B6", Color(0xFFF472B6), Color(0xFFDB2777), Color(0x66F472B6))
        val DARK_74 = AppThemeColor("dark_74", "#E879F9", Color(0xFFE879F9), Color(0xFFC026D3), Color(0x66E879F9))
        val DARK_75 = AppThemeColor("dark_75", "#FF4081", Color(0xFFFF4081), Color(0xFFF50057), Color(0x66FF4081))
        val DARK_76 = AppThemeColor("dark_76", "#FF69B4", Color(0xFFFF69B4), Color(0xFFC2185B), Color(0x66FF69B4))
        val DARK_77 = AppThemeColor("dark_77", "#F50057", Color(0xFFF50057), Color(0xFFC51162), Color(0x66F50057))
        val DARK_78 = AppThemeColor("dark_78", "#DA70D6", Color(0xFFDA70D6), Color(0xFFBA55D3), Color(0x66DA70D6))
        val DARK_79 = AppThemeColor("dark_79", "#FBCFE8", Color(0xFFFBCFE8), Color(0xFFEC4899), Color(0x66FBCFE8))
        val DARK_80 = AppThemeColor("dark_80", "#C026D3", Color(0xFFC026D3), Color(0xFF86198F), Color(0x66C026D3))

        // 9. Púrpuras, Violetas e Índigos (81-90)
        val DARK_81 = AppThemeColor("dark_81", "#A855F7", Color(0xFFA855F7), Color(0xFF6D28D9), Color(0x66A855F7))
        val DARK_82 = AppThemeColor("dark_82", "#8B5CF6", Color(0xFF8B5CF6), Color(0xFF5B21B6), Color(0x668B5CF6))
        val DARK_83 = AppThemeColor("dark_83", "#C084FC", Color(0xFFC084FC), Color(0xFF9333EA), Color(0x66C084FC))
        val DARK_84 = AppThemeColor("dark_84", "#818CF8", Color(0xFF818CF8), Color(0xFF4338CA), Color(0x66818CF8))
        val DARK_85 = AppThemeColor("dark_85", "#A78BFA", Color(0xFFA78BFA), Color(0xFF7C3AED), Color(0x66A78BFA))
        val DARK_86 = AppThemeColor("dark_86", "#7C3AED", Color(0xFF7C3AED), Color(0xFF5B21B6), Color(0x667C3AED))
        val DARK_87 = AppThemeColor("dark_87", "#9333EA", Color(0xFF9333EA), Color(0xFF6B21A8), Color(0x669333EA))
        val DARK_88 = AppThemeColor("dark_88", "#D8B4FE", Color(0xFFD8B4FE), Color(0xFFA855F7), Color(0x66D8B4FE))
        val DARK_89 = AppThemeColor("dark_89", "#6366F1", Color(0xFF6366F1), Color(0xFF4338CA), Color(0x666366F1))
        val DARK_90 = AppThemeColor("dark_90", "#6D28D9", Color(0xFF6D28D9), Color(0xFF4C1D95), Color(0x666D28D9))

        // 10. Tonos Especiales y Blanco Puro (#100)
        val DARK_91 = AppThemeColor("dark_91", "#93C5FD", Color(0xFF93C5FD), Color(0xFF3B82F6), Color(0x6693C5FD))
        val DARK_92 = AppThemeColor("dark_92", "#DDD6FE", Color(0xFFDDD6FE), Color(0xFF8B5CF6), Color(0x66DDD6FE))
        val DARK_93 = AppThemeColor("dark_93", "#94A3B8", Color(0xFF94A3B8), Color(0xFF475569), Color(0x6694A3B8))
        val DARK_94 = AppThemeColor("dark_94", "#E0E7FF", Color(0xFFE0E7FF), Color(0xFF818CF8), Color(0x66E0E7FF))
        val DARK_95 = AppThemeColor("dark_95", "#E2E8F0", Color(0xFFE2E8F0), Color(0xFF94A3B8), Color(0x66E2E8F0))
        val DARK_96 = AppThemeColor("dark_96", "#CBD5E1", Color(0xFFCBD5E1), Color(0xFF64748B), Color(0x66CBD5E1))
        val DARK_97 = AppThemeColor("dark_97", "#D1D5DB", Color(0xFFD1D5DB), Color(0xFF6B7280), Color(0x66D1D5DB))
        val DARK_98 = AppThemeColor("dark_98", "#F1F5F9", Color(0xFFF1F5F9), Color(0xFF94A3B8), Color(0x66F1F5F9))
        val DARK_99 = AppThemeColor("dark_99", "#F8FAFC", Color(0xFFF8FAFC), Color(0xFFCBD5E1), Color(0x66F8FAFC))
        val DARK_100 = AppThemeColor("dark_100", "#FFFFFF", Color(0xFFFFFFFF), Color(0xFFCBD5E1), Color(0x66FFFFFF))

        // =====================================================================================
        // --- 100 PRESETS PARA TEMA CLARO (Compatibles, nítidos, alto contraste) ---
        // =====================================================================================
        // 1. Azules (1-10)
        val LIGHT_01 = AppThemeColor("light_01", "#1D4ED8", Color(0xFF1D4ED8), Color(0xFF1E40AF), Color(0x661D4ED8))
        val LIGHT_02 = AppThemeColor("light_02", "#1E3A8A", Color(0xFF1E3A8A), Color(0xFF172554), Color(0x661E3A8A))
        val LIGHT_03 = AppThemeColor("light_03", "#172554", Color(0xFF172554), Color(0xFF0F172A), Color(0x66172554))
        val LIGHT_04 = AppThemeColor("light_04", "#1E40AF", Color(0xFF1E40AF), Color(0xFF1E3A8A), Color(0x661E40AF))
        val LIGHT_05 = AppThemeColor("light_05", "#2563EB", Color(0xFF2563EB), Color(0xFF1D4ED8), Color(0x662563EB))
        val LIGHT_06 = AppThemeColor("light_06", "#102A6B", Color(0xFF102A6B), Color(0xFF0A1B46), Color(0x66102A6B))
        val LIGHT_07 = AppThemeColor("light_07", "#1E3A5F", Color(0xFF1E3A5F), Color(0xFF142640), Color(0x661E3A5F))
        val LIGHT_08 = AppThemeColor("light_08", "#0F1E40", Color(0xFF0F1E40), Color(0xFF081024), Color(0x660F1E40))
        val LIGHT_09 = AppThemeColor("light_09", "#154360", Color(0xFF154360), Color(0xFF0E2E42), Color(0x66154360))
        val LIGHT_10 = AppThemeColor("light_10", "#0D1B2A", Color(0xFF0D1B2A), Color(0xFF050B12), Color(0x660D1B2A))

        // 2. Cianes y Turquesas (11-20) - Incluye el Teal del sistema #14B8A6 compatible con ambos temas
        val LIGHT_11 = AppThemeColor("light_11", "#0E7490", Color(0xFF0E7490), Color(0xFF155E75), Color(0x660E7490))
        val LIGHT_TEAL = AppThemeColor("light_teal", "#14B8A6", Color(0xFF14B8A6), Color(0xFF0F766E), Color(0x6614B8A6))
        val LIGHT_13 = AppThemeColor("light_13", "#0F766E", Color(0xFF0F766E), Color(0xFF115E59), Color(0x660F766E))
        val LIGHT_14 = AppThemeColor("light_14", "#164E63", Color(0xFF164E63), Color(0xFF0E3A4A), Color(0x66164E63))
        val LIGHT_15 = AppThemeColor("light_15", "#115E59", Color(0xFF115E59), Color(0xFF042F2E), Color(0x66115E59))
        val LIGHT_16 = AppThemeColor("light_16", "#0D9488", Color(0xFF0D9488), Color(0xFF0F766E), Color(0x660D9488))
        val LIGHT_17 = AppThemeColor("light_17", "#004D40", Color(0xFF004D40), Color(0xFF00332C), Color(0x66004D40))
        val LIGHT_18 = AppThemeColor("light_18", "#083344", Color(0xFF083344), Color(0xFF041C26), Color(0x66083344))
        val LIGHT_19 = AppThemeColor("light_19", "#0B4F48", Color(0xFF0B4F48), Color(0xFF07332E), Color(0x660B4F48))
        val LIGHT_20 = AppThemeColor("light_20", "#0E4D5E", Color(0xFF0E4D5E), Color(0xFF09333E), Color(0x660E4D5E))

        // 3. Verdes y Esmeraldas (21-30)
        val LIGHT_21 = AppThemeColor("light_21", "#047857", Color(0xFF047857), Color(0xFF065F46), Color(0x66047857))
        val LIGHT_22 = AppThemeColor("light_22", "#059669", Color(0xFF059669), Color(0xFF047857), Color(0x66059669))
        val LIGHT_23 = AppThemeColor("light_23", "#022C22", Color(0xFF022C22), Color(0xFF011A14), Color(0x66022C22))
        val LIGHT_24 = AppThemeColor("light_24", "#064E3B", Color(0xFF064E3B), Color(0xFF022C22), Color(0x66064E3B))
        val LIGHT_25 = AppThemeColor("light_25", "#14532D", Color(0xFF14532D), Color(0xFF052E16), Color(0x6614532D))
        val LIGHT_26 = AppThemeColor("light_26", "#166534", Color(0xFF166534), Color(0xFF14532D), Color(0x66166534))
        val LIGHT_27 = AppThemeColor("light_27", "#10B981", Color(0xFF10B981), Color(0xFF047857), Color(0x6610B981))
        val LIGHT_28 = AppThemeColor("light_28", "#0E3821", Color(0xFF0E3821), Color(0xFF072113), Color(0x660E3821))
        val LIGHT_29 = AppThemeColor("light_29", "#03442C", Color(0xFF03442C), Color(0xFF022B1C), Color(0x6603442C))
        val LIGHT_30 = AppThemeColor("light_30", "#052E16", Color(0xFF052E16), Color(0xFF02170B), Color(0x66052E16))

        // 4. Limas y Olivas (31-40)
        val LIGHT_31 = AppThemeColor("light_31", "#3F6212", Color(0xFF3F6212), Color(0xFF365314), Color(0x663F6212))
        val LIGHT_32 = AppThemeColor("light_32", "#365314", Color(0xFF365314), Color(0xFF1A2E05), Color(0x66365314))
        val LIGHT_33 = AppThemeColor("light_33", "#1A2E05", Color(0xFF1A2E05), Color(0xFF0D1702), Color(0x661A2E05))
        val LIGHT_34 = AppThemeColor("light_34", "#4D7C0F", Color(0xFF4D7C0F), Color(0xFF365314), Color(0x664D7C0F))
        val LIGHT_35 = AppThemeColor("light_35", "#65A30D", Color(0xFF65A30D), Color(0xFF4D7C0F), Color(0x6665A30D))
        val LIGHT_36 = AppThemeColor("light_36", "#334208", Color(0xFF334208), Color(0xFF1D2604), Color(0x66334208))
        val LIGHT_37 = AppThemeColor("light_37", "#283618", Color(0xFF283618), Color(0xFF17200E), Color(0x66283618))
        val LIGHT_38 = AppThemeColor("light_38", "#384307", Color(0xFF384307), Color(0xFF212704), Color(0x66384307))
        val LIGHT_39 = AppThemeColor("light_39", "#1E2605", Color(0xFF1E2605), Color(0xFF101402), Color(0x661E2605))
        val LIGHT_40 = AppThemeColor("light_40", "#1B2A08", Color(0xFF1B2A08), Color(0xFF0E1704), Color(0x661B2A08))

        // 5. Amarillos, Mostazas y Ocres (41-50)
        val LIGHT_41 = AppThemeColor("light_41", "#92400E", Color(0xFF92400E), Color(0xFF78350F), Color(0x6692400E))
        val LIGHT_42 = AppThemeColor("light_42", "#B45309", Color(0xFFB45309), Color(0xFF92400E), Color(0x66B45309))
        val LIGHT_43 = AppThemeColor("light_43", "#78350F", Color(0xFF78350F), Color(0xFF451A03), Color(0x6678350F))
        val LIGHT_44 = AppThemeColor("light_44", "#854D0E", Color(0xFF854D0E), Color(0xFF713F12), Color(0x66854D0E))
        val LIGHT_45 = AppThemeColor("light_45", "#D97706", Color(0xFFD97706), Color(0xFFB45309), Color(0x66D97706))
        val LIGHT_46 = AppThemeColor("light_46", "#7A3E08", Color(0xFF7A3E08), Color(0xFF472404), Color(0x667A3E08))
        val LIGHT_47 = AppThemeColor("light_47", "#6E3905", Color(0xFF6E3905), Color(0xFF3F2002), Color(0x666E3905))
        val LIGHT_48 = AppThemeColor("light_48", "#61380B", Color(0xFF61380B), Color(0xFF382006), Color(0x6661380B))
        val LIGHT_49 = AppThemeColor("light_49", "#542E07", Color(0xFF542E07), Color(0xFF2F1A04), Color(0x66542E07))
        val LIGHT_50 = AppThemeColor("light_50", "#663300", Color(0xFF663300), Color(0xFF3B1D00), Color(0x66663300))

        // 6. Naranjas Óxido, Terracotas y Ladrillo (51-60)
        val LIGHT_51 = AppThemeColor("light_51", "#C2410C", Color(0xFFC2410C), Color(0xFF9A3412), Color(0x66C2410C))
        val LIGHT_52 = AppThemeColor("light_52", "#9A3412", Color(0xFF9A3412), Color(0xFF7C2D12), Color(0x669A3412))
        val LIGHT_53 = AppThemeColor("light_53", "#7C2D12", Color(0xFF7C2D12), Color(0xFF431407), Color(0x667C2D12))
        val LIGHT_54 = AppThemeColor("light_54", "#EA580C", Color(0xFFEA580C), Color(0xFFC2410C), Color(0x66EA580C))
        val LIGHT_55 = AppThemeColor("light_55", "#C25E00", Color(0xFFC25E00), Color(0xFF8A3B00), Color(0x66C25E00))
        val LIGHT_56 = AppThemeColor("light_56", "#662200", Color(0xFF662200), Color(0xFF3B1400), Color(0x66662200))
        val LIGHT_57 = AppThemeColor("light_57", "#592500", Color(0xFF592500), Color(0xFF331500), Color(0x66592500))
        val LIGHT_58 = AppThemeColor("light_58", "#6E260E", Color(0xFF6E260E), Color(0xFF3E1507), Color(0x666E260E))
        val LIGHT_59 = AppThemeColor("light_59", "#541C0A", Color(0xFF541C0A), Color(0xFF300F05), Color(0x66541C0A))
        val LIGHT_60 = AppThemeColor("light_60", "#451405", Color(0xFF451405), Color(0xFF260A02), Color(0x66451405))

        // 7. Rojos, Granates y Vino Tinto (61-70)
        val LIGHT_61 = AppThemeColor("light_61", "#B91C1C", Color(0xFFB91C1C), Color(0xFF991B1B), Color(0x66B91C1C))
        val LIGHT_62 = AppThemeColor("light_62", "#991B1B", Color(0xFF991B1B), Color(0xFF7F1D1D), Color(0x66991B1B))
        val LIGHT_63 = AppThemeColor("light_63", "#7F1D1D", Color(0xFF7F1D1D), Color(0xFF450A0A), Color(0x667F1D1D))
        val LIGHT_64 = AppThemeColor("light_64", "#DC2626", Color(0xFFDC2626), Color(0xFFB91C1C), Color(0x66DC2626))
        val LIGHT_65 = AppThemeColor("light_65", "#660000", Color(0xFF660000), Color(0xFF330000), Color(0x66660000))
        val LIGHT_66 = AppThemeColor("light_66", "#580C14", Color(0xFF580C14), Color(0xFF31060B), Color(0x66580C14))
        val LIGHT_67 = AppThemeColor("light_67", "#5B0F19", Color(0xFF5B0F19), Color(0xFF32080D), Color(0x665B0F19))
        val LIGHT_68 = AppThemeColor("light_68", "#680014", Color(0xFF680014), Color(0xFF38000B), Color(0x66680014))
        val LIGHT_69 = AppThemeColor("light_69", "#480607", Color(0xFF480607), Color(0xFF280304), Color(0x66480607))
        val LIGHT_70 = AppThemeColor("light_70", "#3B0000", Color(0xFF3B0000), Color(0xFF1F0000), Color(0x663B0000))

        // 8. Rosas, Magentas y Borgoñas (71-80)
        val LIGHT_71 = AppThemeColor("light_71", "#BE185D", Color(0xFFBE185D), Color(0xFF9D174D), Color(0x66BE185D))
        val LIGHT_72 = AppThemeColor("light_72", "#881337", Color(0xFF881337), Color(0xFF4C0519), Color(0x66881337))
        val LIGHT_73 = AppThemeColor("light_73", "#A21CAF", Color(0xFFA21CAF), Color(0xFF86198F), Color(0x66A21CAF))
        val LIGHT_74 = AppThemeColor("light_74", "#9D174D", Color(0xFF9D174D), Color(0xFF831843), Color(0x669D174D))
        val LIGHT_75 = AppThemeColor("light_75", "#701A75", Color(0xFF701A75), Color(0xFF4A044E), Color(0x66701A75))
        val LIGHT_76 = AppThemeColor("light_76", "#4C0519", Color(0xFF4C0519), Color(0xFF29020D), Color(0x664C0519))
        val LIGHT_77 = AppThemeColor("light_77", "#5C0632", Color(0xFF5C0632), Color(0xFF33031C), Color(0x665C0632))
        val LIGHT_78 = AppThemeColor("light_78", "#500724", Color(0xFF500724), Color(0xFF2C0414), Color(0x66500724))
        val LIGHT_79 = AppThemeColor("light_79", "#420420", Color(0xFF420420), Color(0xFF250212), Color(0x66420420))
        val LIGHT_80 = AppThemeColor("light_80", "#38031A", Color(0xFF38031A), Color(0xFF1E010E), Color(0x6638031A))

        // 9. Púrpuras, Violetas e Índigos (81-90)
        val LIGHT_81 = AppThemeColor("light_81", "#6D28D9", Color(0xFF6D28D9), Color(0xFF5B21B6), Color(0x666D28D9))
        val LIGHT_82 = AppThemeColor("light_82", "#7C3AED", Color(0xFF7C3AED), Color(0xFF5B21B6), Color(0x667C3AED))
        val LIGHT_83 = AppThemeColor("light_83", "#4338CA", Color(0xFF4338CA), Color(0xFF3730A3), Color(0x664338CA))
        val LIGHT_84 = AppThemeColor("light_84", "#3730A3", Color(0xFF3730A3), Color(0xFF1E1B4B), Color(0x663730A3))
        val LIGHT_85 = AppThemeColor("light_85", "#3B0764", Color(0xFF3B0764), Color(0xFF1E0338), Color(0x663B0764))
        val LIGHT_86 = AppThemeColor("light_86", "#2E0854", Color(0xFF2E0854), Color(0xFF18042D), Color(0x662E0854))
        val LIGHT_87 = AppThemeColor("light_87", "#311042", Color(0xFF311042), Color(0xFF1A0823), Color(0x66311042))
        val LIGHT_88 = AppThemeColor("light_88", "#240046", Color(0xFF240046), Color(0xFF130026), Color(0x66240046))
        val LIGHT_89 = AppThemeColor("light_89", "#1E1B4B", Color(0xFF1E1B4B), Color(0xFF0F0E2A), Color(0x661E1B4B))
        val LIGHT_90 = AppThemeColor("light_90", "#1A002C", Color(0xFF1A002C), Color(0xFF0E0018), Color(0x661A002C))

        // 10. Tonos Especiales y Negro Grafito (#100)
        val LIGHT_91 = AppThemeColor("light_91", "#334155", Color(0xFF334155), Color(0xFF1E293B), Color(0x66334155))
        val LIGHT_92 = AppThemeColor("light_92", "#312E81", Color(0xFF312E81), Color(0xFF1E1B4B), Color(0x66312E81))
        val LIGHT_93 = AppThemeColor("light_93", "#1E293B", Color(0xFF1E293B), Color(0xFF0F172A), Color(0x661E293B))
        val LIGHT_94 = AppThemeColor("light_94", "#27272A", Color(0xFF27272A), Color(0xFF18181B), Color(0x6627272A))
        val LIGHT_95 = AppThemeColor("light_95", "#262626", Color(0xFF262626), Color(0xFF171717), Color(0x66262626))
        val LIGHT_96 = AppThemeColor("light_96", "#1F2937", Color(0xFF1F2937), Color(0xFF111827), Color(0x661F2937))
        val LIGHT_97 = AppThemeColor("light_97", "#1C1917", Color(0xFF1C1917), Color(0xFF0C0A09), Color(0x661C1917))
        val LIGHT_98 = AppThemeColor("light_98", "#141416", Color(0xFF141416), Color(0xFF0A0A0C), Color(0x66141416))
        val LIGHT_99 = AppThemeColor("light_99", "#18181B", Color(0xFF18181B), Color(0xFF09090B), Color(0x6618181B))
        val LIGHT_100 = AppThemeColor("light_100", "#0F172A", Color(0xFF0F172A), Color(0xFF1E293B), Color(0x660F172A))

        // Backward compatibility constants
        val BLUE = DARK_01
        val RED = DARK_61
        val EMERALD = DARK_21
        val PURPLE = DARK_81
        val AMBER = DARK_43
        val CYAN = DARK_11
        val PINK = DARK_71
        val ORANGE = DARK_51
        val TEAL = DARK_TEAL
        val DEFAULT = DARK_TEAL
        val INDIGO = DARK_84

        val DARK_WHITE = DARK_100
        val LIGHT_BLACK = LIGHT_100

        val presetsDark: List<AppThemeColor> = listOf(
            DARK_01, DARK_02, DARK_03, DARK_04, DARK_05, DARK_06, DARK_07, DARK_08, DARK_09, DARK_10,
            DARK_11, DARK_TEAL, DARK_13, DARK_14, DARK_15, DARK_16, DARK_17, DARK_18, DARK_19, DARK_20,
            DARK_21, DARK_22, DARK_23, DARK_24, DARK_25, DARK_26, DARK_27, DARK_28, DARK_29, DARK_30,
            DARK_31, DARK_32, DARK_33, DARK_34, DARK_35, DARK_36, DARK_37, DARK_38, DARK_39, DARK_40,
            DARK_41, DARK_42, DARK_43, DARK_44, DARK_45, DARK_46, DARK_47, DARK_48, DARK_49, DARK_50,
            DARK_51, DARK_52, DARK_53, DARK_54, DARK_55, DARK_56, DARK_57, DARK_58, DARK_59, DARK_60,
            DARK_61, DARK_62, DARK_63, DARK_64, DARK_65, DARK_66, DARK_67, DARK_68, DARK_69, DARK_70,
            DARK_71, DARK_72, DARK_73, DARK_74, DARK_75, DARK_76, DARK_77, DARK_78, DARK_79, DARK_80,
            DARK_81, DARK_82, DARK_83, DARK_84, DARK_85, DARK_86, DARK_87, DARK_88, DARK_89, DARK_90,
            DARK_91, DARK_92, DARK_93, DARK_94, DARK_95, DARK_96, DARK_97, DARK_98, DARK_99, DARK_100
        )

        val presetsLight: List<AppThemeColor> = listOf(
            LIGHT_01, LIGHT_02, LIGHT_03, LIGHT_04, LIGHT_05, LIGHT_06, LIGHT_07, LIGHT_08, LIGHT_09, LIGHT_10,
            LIGHT_11, LIGHT_TEAL, LIGHT_13, LIGHT_14, LIGHT_15, LIGHT_16, LIGHT_17, LIGHT_18, LIGHT_19, LIGHT_20,
            LIGHT_21, LIGHT_22, LIGHT_23, LIGHT_24, LIGHT_25, LIGHT_26, LIGHT_27, LIGHT_28, LIGHT_29, LIGHT_30,
            LIGHT_31, LIGHT_32, LIGHT_33, LIGHT_34, LIGHT_35, LIGHT_36, LIGHT_37, LIGHT_38, LIGHT_39, LIGHT_40,
            LIGHT_41, LIGHT_42, LIGHT_43, LIGHT_44, LIGHT_45, LIGHT_46, LIGHT_47, LIGHT_48, LIGHT_49, LIGHT_50,
            LIGHT_51, LIGHT_52, LIGHT_53, LIGHT_54, LIGHT_55, LIGHT_56, LIGHT_57, LIGHT_58, LIGHT_59, LIGHT_60,
            LIGHT_61, LIGHT_62, LIGHT_63, LIGHT_64, LIGHT_65, LIGHT_66, LIGHT_67, LIGHT_68, LIGHT_69, LIGHT_70,
            LIGHT_71, LIGHT_72, LIGHT_73, LIGHT_74, LIGHT_75, LIGHT_76, LIGHT_77, LIGHT_78, LIGHT_79, LIGHT_80,
            LIGHT_81, LIGHT_82, LIGHT_83, LIGHT_84, LIGHT_85, LIGHT_86, LIGHT_87, LIGHT_88, LIGHT_89, LIGHT_90,
            LIGHT_91, LIGHT_92, LIGHT_93, LIGHT_94, LIGHT_95, LIGHT_96, LIGHT_97, LIGHT_98, LIGHT_99, LIGHT_100
        )

        fun getPresetsForTheme(isDarkTheme: Boolean): List<AppThemeColor> {
            return if (isDarkTheme) presetsDark else presetsLight
        }

        /**
         * Returns the 1-to-1 matching color in the target theme palette.
         * Ensures that switching theme mode maintains the exact color/chromatic identity,
         * with White in Dark theme mapping to Black in Light theme.
         */
        fun getCorrespondingColor(currentColor: AppThemeColor, targetIsDark: Boolean): AppThemeColor {
            val targetList = if (targetIsDark) presetsDark else presetsLight
            val otherList = if (targetIsDark) presetsLight else presetsDark

            // If it's already in the target palette by id or primary color, keep it
            val indexInTarget = targetList.indexOfFirst { it.id == currentColor.id || it.primary == currentColor.primary }
            if (indexInTarget >= 0) {
                return targetList[indexInTarget]
            }

            // Map by 1-to-1 index from the other palette
            val indexInOther = otherList.indexOfFirst { it.id == currentColor.id || it.primary == currentColor.primary }
            if (indexInOther in targetList.indices) {
                return targetList[indexInOther]
            }

            // Fallback: Teal del sistema
            return if (targetIsDark) DARK_TEAL else LIGHT_TEAL
        }

        val entries: List<AppThemeColor> = presetsDark + presetsLight

        fun fromCustomColor(color: Color): AppThemeColor {
            val r = (color.red * 255f).toInt().coerceIn(0, 255)
            val g = (color.green * 255f).toInt().coerceIn(0, 255)
            val b = (color.blue * 255f).toInt().coerceIn(0, 255)
            val hex = String.format("#%02X%02X%02X", r, g, b)
            val darkVariant = Color(
                (r * 0.72f).toInt().coerceIn(0, 255),
                (g * 0.72f).toInt().coerceIn(0, 255),
                (b * 0.72f).toInt().coerceIn(0, 255)
            )
            return AppThemeColor(
                id = "custom_$hex",
                displayName = hex,
                primary = color,
                primaryVariant = darkVariant,
                glowColor = color.copy(alpha = 0.4f)
            )
        }

        fun fromId(id: String?): AppThemeColor {
            if (id.isNullOrEmpty() || id.equals("teal", ignoreCase = true) || id.equals("dark_teal", ignoreCase = true) || id.equals("light_teal", ignoreCase = true)) {
                return DARK_TEAL
            }
            val match = entries.find { it.id.equals(id, ignoreCase = true) }
            if (match != null) return match

            val cleanHex = id.removePrefix("custom_").removePrefix("CUSTOM_").removePrefix("#")
            if (cleanHex.length == 6 || cleanHex.length == 8) {
                try {
                    val parsedInt = android.graphics.Color.parseColor("#$cleanHex")
                    val color = Color(parsedInt)
                    return fromCustomColor(color)
                } catch (_: Exception) {}
            }
            return DARK_TEAL
        }
    }
}
