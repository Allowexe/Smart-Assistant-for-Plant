package fr.isen.veith.sap.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.isen.veith.sap.domain.model.Plant
import fr.isen.veith.sap.ui.theme.*

/**
 * Menu déroulant des plantes enregistrées.
 * S'affiche en overlay sous le nom de la plante sélectionnée.
 */
@Composable
fun PlantDropdownMenu(
    plants: List<Plant>,
    selectedPlant: Plant,
    isOpen: Boolean,
    onSelectPlant: (Plant) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isOpen,
        enter = expandVertically(
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            expandFrom    = Alignment.Top
        ) + fadeIn(),
        exit  = shrinkVertically(
            animationSpec = tween(200),
            shrinkTowards = Alignment.Top
        ) + fadeOut()
    ) {
        Column(
            modifier = modifier
                .shadow(elevation = 12.dp, shape = RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    width = 1.dp,
                    color = Green600.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(vertical = 6.dp)
        ) {
            plants.forEach { plant ->
                val isSelected = plant.id == selectedPlant.id
                PlantMenuItem(
                    plant      = plant,
                    isSelected = isSelected,
                    onClick    = {
                        onSelectPlant(plant)
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
private fun PlantMenuItem(
    plant: Plant,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (isSelected) Green800.copy(alpha = 0.25f)
                else            androidx.compose.ui.graphics.Color.Transparent
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Emoji plante
        Text(text = plant.emoji, fontSize = 22.sp)

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text  = plant.commonName,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSelected) Green200
                else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
            )
            Text(
                text  = plant.scientificName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
            )
        }

        // Indicateur de sélection
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(Orange400)
            )
        }
    }
}