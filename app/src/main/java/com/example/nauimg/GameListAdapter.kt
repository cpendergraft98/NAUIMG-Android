package com.example.nauimg

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import android.content.res.Configuration
class GameListAdapter(private val games: List<Game>, private val itemClickListener: (Game) -> Unit) :
    RecyclerView.Adapter<GameListAdapter.GameViewHolder>() {

    private var selectedPosition: Int = -1

    inner class GameViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val gameName: TextView = itemView.findViewById(R.id.gameName)
        private val gameIcon: ImageView = itemView.findViewById(R.id.gameIcon)
        private val container: LinearLayout = itemView.findViewById(R.id.container)

        fun bind(game: Game, position: Int) {
            gameName.text = game.label

            // Set the game icon (using the default app icon for now)
            gameIcon.setImageResource(R.mipmap.ic_launcher)

            // Highlight the selected item
            if (position == selectedPosition) {
                container.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.selected_item_color))
            } else {
                container.setBackgroundColor(ContextCompat.getColor(itemView.context, android.R.color.transparent))
            }

            // Detect dark mode and set text color accordingly
            val nightModeFlags = itemView.context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            when (nightModeFlags) {
                Configuration.UI_MODE_NIGHT_YES -> {
                    gameName.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.white))
                }
                Configuration.UI_MODE_NIGHT_NO -> {
                    gameName.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.black))
                }
                Configuration.UI_MODE_NIGHT_UNDEFINED -> {
                    // Default color if not defined
                    gameName.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.black))
                }
            }

            itemView.setOnClickListener {
                selectedPosition = position
                notifyDataSetChanged()
                itemClickListener(game)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GameViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_game, parent, false)
        return GameViewHolder(view)
    }

    override fun onBindViewHolder(holder: GameViewHolder, position: Int) {
        holder.bind(games[position], position)
    }

    override fun getItemCount(): Int = games.size
}